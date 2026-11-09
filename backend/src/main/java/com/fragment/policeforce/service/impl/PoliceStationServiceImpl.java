package com.fragment.policeforce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.common.redis.StationCatalogCache;
import com.fragment.policeforce.common.util.TextUtil;
import com.fragment.policeforce.dto.PoliceStationAddDTO;
import com.fragment.policeforce.dto.PoliceStationPageQueryDTO;
import com.fragment.policeforce.dto.PoliceStationQueryDTO;
import com.fragment.policeforce.dto.PoliceStationUpdateDTO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.entity.SysDictData;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.service.PoliceStationService;
import com.fragment.policeforce.service.DutySlotService;
import com.fragment.policeforce.service.SysDictDataService;
import com.fragment.policeforce.vo.PoliceStationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PoliceStationServiceImpl extends ServiceImpl<PoliceStationMapper, PoliceStation>
        implements PoliceStationService {

    private static final String RESOURCE_TYPE_DICT = "station_type";
    private static final String RESOURCE_STATUS_DICT = "resource_status";

    @Autowired
    private SysDictDataService sysDictDataService;

    @Autowired
    private DutySlotService dutySlotService;

    @Autowired
    private AllocationMapper reservationMapper;

    @Autowired
    private StationCatalogCache resourceCatalogCache;

    @Autowired
    private AdminAuditHelper adminAuditHelper;

    @Override
    public List<PoliceStationVO> search(PoliceStationQueryDTO queryDTO) {
        PoliceStationQueryDTO actualQuery = queryDTO == null ? new PoliceStationQueryDTO() : queryDTO;
        if (hasStationFilters(actualQuery)) {
            return loadStationList(actualQuery);
        }
        return resourceCatalogCache.getHomeStationList(() -> loadStationList(actualQuery));
    }

    @Override
    public Page<PoliceStationVO> pageResource(PoliceStationPageQueryDTO queryDTO) {
        PoliceStationPageQueryDTO actualQuery = queryDTO == null ? new PoliceStationPageQueryDTO() : queryDTO;

        long pageNum = actualQuery.getPageNum() == null ? 1L : actualQuery.getPageNum();
        long pageSize = actualQuery.getPageSize() == null ? 10L : actualQuery.getPageSize();

        Page<PoliceStation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PoliceStation> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(
                StringUtils.hasText(actualQuery.getStationType()),
                PoliceStation::getStationType,
                actualQuery.getStationType()
        );

        wrapper.eq(
                StringUtils.hasText(actualQuery.getStatus()),
                PoliceStation::getStatus,
                actualQuery.getStatus()
        );

        wrapper.and(StringUtils.hasText(actualQuery.getKeyword()), w -> w
                .like(PoliceStation::getStationCode, actualQuery.getKeyword())
                .or()
                .like(PoliceStation::getStationName, actualQuery.getKeyword())
                .or()
                .like(PoliceStation::getLocation, actualQuery.getKeyword()));

        wrapper.orderByAsc(PoliceStation::getStationCode);

        Page<PoliceStation> resourcePage = this.page(page, wrapper);

        Map<String, String> resourceTypeLabelMap = sysDictDataService.getDictLabelMap(RESOURCE_TYPE_DICT);
        Map<String, String> resourceStatusLabelMap = sysDictDataService.getDictLabelMap(RESOURCE_STATUS_DICT);

        List<PoliceStationVO> voRecords = resourcePage.getRecords().stream()
                .map(station -> toStationVO(station, resourceTypeLabelMap, resourceStatusLabelMap))
                .collect(Collectors.toList());

        Page<PoliceStationVO> voPage = new Page<>(
                resourcePage.getCurrent(),
                resourcePage.getSize(),
                resourcePage.getTotal()
        );
        voPage.setRecords(voRecords);
        return voPage;
    }

    @Override
    public PoliceStationVO getStationById(Long id) {
        if (id == null) {
            throw new BusinessException("资源ID不能为空");
        }

        PoliceStation station = this.getById(id);
        if (station == null) {
            throw new BusinessException("资源不存在");
        }

        Map<String, String> resourceTypeLabelMap = sysDictDataService.getDictLabelMap(RESOURCE_TYPE_DICT);
        Map<String, String> resourceStatusLabelMap = sysDictDataService.getDictLabelMap(RESOURCE_STATUS_DICT);
        return toStationVO(station, resourceTypeLabelMap, resourceStatusLabelMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addResource(PoliceStationAddDTO dto) {
        PoliceStation station = new PoliceStation();
        adminAuditHelper.execute(
                "RESOURCE",
                "ADD",
                "RESOURCE",
                station::getId,
                () -> "stationCode=" + TextUtil.trimToNull(dto.getStationCode())
                        + ", stationName=" + TextUtil.trimToNull(dto.getStationName())
                        + ", status=" + TextUtil.trimToNull(dto.getStatus()),
                () -> {
                    String stationCode = dto.getStationCode().trim();
                    String stationName = dto.getStationName().trim();
                    String stationType = dto.getStationType().trim();
                    String status = dto.getStatus().trim();

                    validateStationCodeUnique(stationCode, null);
                    validateDictValueExists(RESOURCE_TYPE_DICT, stationType, "资源类型不存在");
                    validateDictValueExists(RESOURCE_STATUS_DICT, status, "资源状态不存在");

                    station.setStationCode(stationCode);
                    station.setStationName(stationName);
                    station.setStationType(stationType);
                    station.setStatus(status);
                    station.setLocation(TextUtil.trimToNull(dto.getLocation()));
                    station.setDescription(TextUtil.trimToNull(dto.getDescription()));

                    boolean saved = this.save(station);
                    if (!saved) {
                        throw new BusinessException("新增资源失败");
                    }

                    resourceCatalogCache.invalidateHomeStationList();
                }
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateResource(PoliceStationUpdateDTO dto) {
        PoliceStation existing = this.getById(dto.getId());
        if (existing == null) {
            throw new BusinessException("资源不存在");
        }
        adminAuditHelper.execute(
                "RESOURCE",
                "UPDATE",
                "RESOURCE",
                existing::getId,
                () -> "stationCode=" + existing.getStationCode()
                        + ", stationName=" + existing.getStationName()
                        + ", status=" + existing.getStatus(),
                () -> {
                    String stationCode = dto.getStationCode().trim();
                    String stationName = dto.getStationName().trim();
                    String stationType = dto.getStationType().trim();
                    String status = dto.getStatus().trim();

                    validateStationCodeUnique(stationCode, existing.getId());
                    validateDictValueExists(RESOURCE_TYPE_DICT, stationType, "资源类型不存在");
                    validateDictValueExists(RESOURCE_STATUS_DICT, status, "资源状态不存在");

                    existing.setStationCode(stationCode);
                    existing.setStationName(stationName);
                    existing.setStationType(stationType);
                    existing.setStatus(status);
                    existing.setLocation(TextUtil.trimToNull(dto.getLocation()));
                    existing.setDescription(TextUtil.trimToNull(dto.getDescription()));

                    boolean updated = this.updateById(existing);
                    if (!updated) {
                        throw new BusinessException("更新资源失败");
                    }

                    resourceCatalogCache.invalidateHomeStationList();
                }
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeResource(Long id) {
        final PoliceStation[] resourceHolder = new PoliceStation[1];
        adminAuditHelper.execute(
                "RESOURCE",
                "DELETE",
                "RESOURCE",
                () -> id,
                () -> {
                    PoliceStation station = resourceHolder[0];
                    return station == null
                            ? "stationId=" + id
                            : "stationCode=" + station.getStationCode() + ", stationName=" + station.getStationName();
                },
                () -> {
                    if (id == null) {
                        throw new BusinessException("资源ID不能为空");
                    }

                    PoliceStation station = this.getById(id);
                    if (station == null) {
                        throw new BusinessException("资源不存在");
                    }
                    resourceHolder[0] = station;

                    long slotCount = dutySlotService.count(
                            new LambdaQueryWrapper<DutySlot>()
                                    .eq(DutySlot::getStationId, id)
                    );
                    if (slotCount > 0) {
                        throw new BusinessException("当前资源还有关联时段，不能删除");
                    }

                    long bookedAllocationCount = reservationMapper.selectCount(
                            new LambdaQueryWrapper<AllocationAssignment>()
                                    .eq(AllocationAssignment::getStationId, id)
                                    .eq(AllocationAssignment::getStatus, AllocationStatusConstants.BOOKED)
                    );
                    if (bookedAllocationCount > 0) {
                        throw new BusinessException("当前资源还有生效中的预约，不能删除");
                    }

                    boolean removed = this.removeById(id);
                    if (!removed) {
                        throw new BusinessException("删除资源失败");
                    }

                    resourceCatalogCache.invalidateHomeStationList();
                }
        );
    }

    private void validateStationCodeUnique(String stationCode, Long excludeId) {
        LambdaQueryWrapper<PoliceStation> wrapper = new LambdaQueryWrapper<PoliceStation>()
                .eq(PoliceStation::getStationCode, stationCode);

        if (excludeId != null) {
            wrapper.ne(PoliceStation::getId, excludeId);
        }

        long count = this.count(wrapper);
        if (count > 0) {
            throw new BusinessException("资源编号已存在");
        }
    }

    private boolean hasStationFilters(PoliceStationQueryDTO queryDTO) {
        return StringUtils.hasText(queryDTO.getName())
                || StringUtils.hasText(queryDTO.getType())
                || StringUtils.hasText(queryDTO.getStatus());
    }

    private List<PoliceStationVO> loadStationList(PoliceStationQueryDTO queryDTO) {
        List<PoliceStation> stations = this.list(new LambdaQueryWrapper<PoliceStation>()
                .like(StringUtils.hasText(queryDTO.getName()),
                        PoliceStation::getStationName, queryDTO.getName())
                .eq(StringUtils.hasText(queryDTO.getType()),
                        PoliceStation::getStationType, queryDTO.getType())
                .eq(StringUtils.hasText(queryDTO.getStatus()),
                        PoliceStation::getStatus, queryDTO.getStatus())
                .orderByAsc(PoliceStation::getStationCode));

        Map<String, String> resourceTypeLabelMap = sysDictDataService.getDictLabelMap(RESOURCE_TYPE_DICT);
        Map<String, String> resourceStatusLabelMap = sysDictDataService.getDictLabelMap(RESOURCE_STATUS_DICT);
        return stations.stream()
                .map(station -> toStationVO(station, resourceTypeLabelMap, resourceStatusLabelMap))
                .collect(Collectors.toList());
    }

    private void validateDictValueExists(String dictType, String dictValue, String message) {
        long count = sysDictDataService.count(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getDictValue, dictValue));

        if (count <= 0) {
            throw new BusinessException(message);
        }
    }

    private PoliceStationVO toStationVO(PoliceStation station,
                                    Map<String, String> resourceTypeLabelMap,
                                    Map<String, String> resourceStatusLabelMap) {
        PoliceStationVO resourceVO = new PoliceStationVO();
        BeanUtils.copyProperties(station, resourceVO);
        resourceVO.setStationTypeDesc(
                resourceTypeLabelMap.getOrDefault(station.getStationType(), station.getStationType())
        );
        resourceVO.setStatusDesc(
                resourceStatusLabelMap.getOrDefault(station.getStatus(), station.getStatus())
        );
        return resourceVO;
    }
}
