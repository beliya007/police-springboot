package com.fragment.policeforce.allocation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import org.springframework.stereotype.Component;

/**
 * 把预约记录写进 MySQL 的那一步，被普通和热门两条路径共用。
 */
@Component
public class AllocationPersistenceHelper {

    /** 预约号撞车时最多重试几次 */
    private static final int RESERVATION_NO_RETRY_TIMES = 3;

    private final AllocationNoGenerator reservationNoGenerator;        // 生成预约号（如 RES2026xxxx）
    private final AllocationMapper reservationMapper;                  // 预约表操作
    private final AllocationAutoCancelService reservationAutoCancelService;  // 用来填"自动取消截止时间"

    public AllocationPersistenceHelper(AllocationNoGenerator reservationNoGenerator,
                                        AllocationMapper reservationMapper,
                                        AllocationAutoCancelService reservationAutoCancelService) {
        this.reservationNoGenerator = reservationNoGenerator;
        this.reservationMapper = reservationMapper;
        this.reservationAutoCancelService = reservationAutoCancelService;
    }

    /**
     * 组装一条 AllocationAssignment 实体：
     */
    public AllocationAssignment buildReservation(Long userId, Long officerId, Long stationId, Long slotId,
                                                 PoliceStation station, DutySlot slot) {
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setUserId(userId);
        allocation.setOfficerId(officerId);
        allocation.setStationId(stationId);
        allocation.setSlotId(slotId);
        allocation.setStationName(station.getStationName());
        allocation.setStationCode(station.getStationCode());
        allocation.setStationLocation(station.getLocation());
        allocation.setSlotStartDatetime(slot.getStartDatetime());
        allocation.setSlotEndDatetime(slot.getEndDatetime());
        allocation.setIsActive(1);                      // 标记为活跃预约
        allocation.setStatus("BOOKED");                 // 状态：已预约
        allocation.setSourceType(slot.getSlotType());   // 记录来源类型：HOT / NORMAL
        reservationAutoCancelService.fillAutoCancelDeadline(allocation);  // 算好"未签到自动取消"的截止时间
        return allocation;
    }

    /**
     * 落库，如果撞了"同一警员同一时段的活跃预约"就抛 409。
     */
    public void saveWithRetry(AllocationAssignment allocation) {
        if (saveIfNoActiveConflict(allocation) == SaveOutcome.DUPLICATE) {
            throw new BusinessException(409, "该警员已被调配到该时段，不能重复派出");
        }
    }

    /**
     * 真正落库 + 防重：
     */
    public SaveOutcome saveIfNoActiveConflict(AllocationAssignment allocation) {
        for (int attempt = 0; attempt < RESERVATION_NO_RETRY_TIMES; attempt++) {
            // 每次尝试都重新生成一个预约号。
            allocation.setAllocationNo(reservationNoGenerator.nextAllocationNo());
            // insertIgnore 同时处理预约号碰撞和"一名警员同一时段"的冲突。
            if (reservationMapper.insertIgnore(allocation) > 0) {
                return SaveOutcome.CREATED;   // 插入成功
            }
            // 插入失败
            if (hasActiveReservation(allocation.getOfficerId(), allocation.getSlotId())) {
                return SaveOutcome.DUPLICATE;  // 已存在活跃预约 → 重复
            }
        }
        // 数据异常，抛业务异常。
        throw new BusinessException("创建预约失败，请重试");
    }

    /**
     * 查"同一警员同一时段"是否已有活跃预约。
     */
    private boolean hasActiveReservation(Long officerId, Long slotId) {
        return reservationMapper.selectOne(new LambdaQueryWrapper<AllocationAssignment>()
                .select(AllocationAssignment::getId)
                .eq(AllocationAssignment::getOfficerId, officerId)
                .eq(AllocationAssignment::getSlotId, slotId)
                .eq(AllocationAssignment::getIsActive, 1)
                .last("LIMIT 1 FOR UPDATE")) != null;
    }

    /** 落库结果枚举：CREATED=插入成功，DUPLICATE=撞了活跃预约。 */
    public enum SaveOutcome {
        CREATED,
        DUPLICATE
    }
}
