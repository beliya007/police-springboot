package com.fragment.policeforce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.common.util.TextUtil;
import com.fragment.policeforce.dto.DutySlotAddDTO;
import com.fragment.policeforce.dto.DutySlotPageQueryDTO;
import com.fragment.policeforce.dto.DutySlotUpdateDTO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.mapper.DutySlotMapper;
import com.fragment.policeforce.service.DutySlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DutySlotServiceImpl extends ServiceImpl<DutySlotMapper, DutySlot>
        implements DutySlotService {

    @Autowired
    private PoliceStationMapper policeStationMapper;

    @Autowired
    private AllocationMapper reservationMapper;

    @Autowired
    private HotAllocationRedisService hotAllocationRedisService;

    @Autowired
    private AdminAuditHelper adminAuditHelper;

    @Override
    public List<DutySlot> getSlotsByStationId(Long stationId) {
        return this.list(new LambdaQueryWrapper<DutySlot>()
                .eq(DutySlot::getStationId, stationId)
                .eq(DutySlot::getStatus, DutySlotStatusConstants.OPEN)
                .orderByAsc(DutySlot::getStartDatetime));
    }

    @Override
    public DutySlot getStationSlotById(Long id) {
        if (id == null) {
            throw new BusinessException("时段ID不能为空");
        }

        DutySlot slot = this.getById(id);
        if (slot == null) {
            throw new BusinessException("时段不存在");
        }

        return slot;
    }

    @Override
    public Page<DutySlot> pageStationSlot(DutySlotPageQueryDTO queryDTO) {
        DutySlotPageQueryDTO actualQuery = queryDTO == null ? new DutySlotPageQueryDTO() : queryDTO;

        long pageNum = actualQuery.getPageNum() == null ? 1L : actualQuery.getPageNum();
        long pageSize = actualQuery.getPageSize() == null ? 10L : actualQuery.getPageSize();

        Page<DutySlot> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DutySlot> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(actualQuery.getStationId() != null, DutySlot::getStationId, actualQuery.getStationId());
        wrapper.eq(StringUtils.hasText(actualQuery.getStatus()), DutySlot::getStatus, actualQuery.getStatus());
        wrapper.eq(StringUtils.hasText(actualQuery.getSlotType()), DutySlot::getSlotType, actualQuery.getSlotType());
        wrapper.orderByAsc(DutySlot::getStartDatetime)
                .orderByAsc(DutySlot::getId);

        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStationSlot(DutySlotAddDTO dto) {
        DutySlot slot = new DutySlot();
        adminAuditHelper.execute(
                "RESOURCE_SLOT",
                "ADD",
                "RESOURCE_SLOT",
                slot::getId,
                () -> "stationId=" + dto.getStationId()
                        + ", slotType=" + TextUtil.trimToNull(dto.getSlotType())
                        + ", status=" + TextUtil.trimToNull(dto.getStatus())
                        + ", totalQuota=" + dto.getTotalQuota(),
                () -> {
                    PoliceStation station = policeStationMapper.selectById(dto.getStationId());
                    if (station == null) {
                        throw new BusinessException("资源不存在");
                    }

                    NormalizedSlotInput slotInput = normalizeAndValidateInput(
                            dto.getStartDatetime(),
                            dto.getEndDatetime(),
                            dto.getSlotType(),
                            dto.getOpenTime(),
                            dto.getTotalQuota(),
                            dto.getStatus()
                    );

                    slot.setStationId(dto.getStationId());
                    slot.setStartDatetime(slotInput.startDatetime());
                    slot.setEndDatetime(slotInput.endDatetime());
                    slot.setSlotType(slotInput.slotType());
                    slot.setOpenTime(slotInput.openTime());
                    slot.setTotalQuota(slotInput.totalQuota());
                    slot.setRemainQuota(slotInput.totalQuota());
                    slot.setStatus(slotInput.status());

                    boolean saved = this.save(slot);
                    if (!saved) {
                        throw new BusinessException("新增时段失败");
                    }

                    hotAllocationRedisService.syncSlotCache(slot);
                }
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStationSlot(DutySlotUpdateDTO dto) {
        DutySlot existing = getStationSlotById(dto.getId());
        adminAuditHelper.execute(
                "RESOURCE_SLOT",
                "UPDATE",
                "RESOURCE_SLOT",
                existing::getId,
                () -> "stationId=" + existing.getStationId()
                        + ", slotType=" + existing.getSlotType()
                        + ", status=" + existing.getStatus()
                        + ", totalQuota=" + existing.getTotalQuota(),
                () -> {
                    NormalizedSlotInput slotInput = normalizeAndValidateInput(
                            dto.getStartDatetime(),
                            dto.getEndDatetime(),
                            dto.getSlotType(),
                            dto.getOpenTime(),
                            dto.getTotalQuota(),
                            dto.getStatus()
                    );

                    long bookedCount = countBookedReservation(dto.getId());
                    if (bookedCount > 0) {
                        validateBookedSlotEditable(existing, slotInput);
                    }

                    int occupiedQuota = calculateOccupiedQuota(existing);
                    if (slotInput.totalQuota() < occupiedQuota) {
                        throw new BusinessException("新的总名额不能小于已占用名额");
                    }

                    existing.setStartDatetime(slotInput.startDatetime());
                    existing.setEndDatetime(slotInput.endDatetime());
                    existing.setSlotType(slotInput.slotType());
                    existing.setOpenTime(slotInput.openTime());
                    existing.setTotalQuota(slotInput.totalQuota());
                    existing.setRemainQuota(slotInput.totalQuota() - occupiedQuota);
                    existing.setStatus(slotInput.status());

                    boolean updated = this.updateById(existing);
                    if (!updated) {
                        throw new BusinessException("更新时段失败");
                    }

                    hotAllocationRedisService.syncSlotCache(existing);
                }
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStationSlot(Long id) {
        final DutySlot[] slotHolder = new DutySlot[1];
        adminAuditHelper.execute(
                "RESOURCE_SLOT",
                "DELETE",
                "RESOURCE_SLOT",
                () -> id,
                () -> {
                    DutySlot slot = slotHolder[0];
                    return slot == null
                            ? "slotId=" + id
                            : "stationId=" + slot.getStationId()
                                    + ", slotType=" + slot.getSlotType()
                                    + ", start=" + slot.getStartDatetime();
                },
                () -> {
                    if (id == null) {
                        throw new BusinessException("时段ID不能为空");
                    }

                    DutySlot existing = this.getById(id);
                    if (existing == null) {
                        throw new BusinessException("时段不存在");
                    }
                    slotHolder[0] = existing;

                    long bookedCount = countBookedReservation(id);
                    if (bookedCount > 0) {
                        throw new BusinessException("当前时段还有生效中的预约，不能删除");
                    }

                    boolean removed = this.removeById(id);
                    if (!removed) {
                        throw new BusinessException("删除时段失败");
                    }

                    hotAllocationRedisService.invalidateSlotCache(id);
                }
        );
    }

    @Override
    public void deductQuotaIfAvailable(Long slotId) {
        LambdaUpdateWrapper<DutySlot> updateWrapper = new LambdaUpdateWrapper<DutySlot>()
                .eq(DutySlot::getId, slotId)
                .eq(DutySlot::getStatus, DutySlotStatusConstants.OPEN)
                .gt(DutySlot::getRemainQuota, 0)
                .setSql("remain_quota = remain_quota - 1");

        boolean updated = this.update(updateWrapper);
        if (!updated) {
            throw new BusinessException(409, "时段不可预约或余量不足");
        }
    }

    @Override
    public void restoreQuota(Long slotId) {
        LambdaUpdateWrapper<DutySlot> updateWrapper = new LambdaUpdateWrapper<DutySlot>()
                .eq(DutySlot::getId, slotId)
                .setSql("remain_quota = remain_quota + 1");

        boolean updated = this.update(updateWrapper);
        if (!updated) {
            throw new BusinessException("回补时段余量失败");
        }
    }

    private NormalizedSlotInput normalizeAndValidateInput(LocalDateTime startDatetime,
                                                          LocalDateTime endDatetime,
                                                          String slotType,
                                                          LocalDateTime openTime,
                                                          Integer totalQuota,
                                                          String status) {
        if (!startDatetime.isBefore(endDatetime)) {
            throw new BusinessException("开始时间必须早于结束时间");
        }

        String normalizedSlotType = slotType.trim().toUpperCase();
        if (!DutySlotTypeConstants.NORMAL.equals(normalizedSlotType)
                && !DutySlotTypeConstants.HOT.equals(normalizedSlotType)) {
            throw new BusinessException("时段类型只能是 NORMAL 或 HOT");
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!DutySlotStatusConstants.OPEN.equals(normalizedStatus)
                && !DutySlotStatusConstants.CLOSED.equals(normalizedStatus)) {
            throw new BusinessException("时段状态只能是 OPEN 或 CLOSED");
        }

        if (DutySlotTypeConstants.HOT.equals(normalizedSlotType) && openTime == null) {
            throw new BusinessException("热门时段必须设置开放预约时间");
        }

        return new NormalizedSlotInput(
                startDatetime,
                endDatetime,
                normalizedSlotType,
                openTime,
                totalQuota,
                normalizedStatus
        );
    }

    private void validateBookedSlotEditable(DutySlot existing, NormalizedSlotInput slotInput) {
        if (!existing.getStartDatetime().equals(slotInput.startDatetime())) {
            throw new BusinessException("当前时段已有预约，不允许修改开始时间");
        }

        if (!existing.getEndDatetime().equals(slotInput.endDatetime())) {
            throw new BusinessException("当前时段已有预约，不允许修改结束时间");
        }

        if (!existing.getSlotType().equals(slotInput.slotType())) {
            throw new BusinessException("当前时段已有预约，不允许修改时段类型");
        }
    }

    private int calculateOccupiedQuota(DutySlot existing) {
        return existing.getTotalQuota() - existing.getRemainQuota();
    }

    private long countBookedReservation(Long slotId) {
        return reservationMapper.selectCount(new LambdaQueryWrapper<AllocationAssignment>()
                .eq(AllocationAssignment::getSlotId, slotId)
                .eq(AllocationAssignment::getStatus, AllocationStatusConstants.BOOKED));
    }

    private record NormalizedSlotInput(LocalDateTime startDatetime,
                                       LocalDateTime endDatetime,
                                       String slotType,
                                       LocalDateTime openTime,
                                       Integer totalQuota,
                                       String status) {
    }
}
