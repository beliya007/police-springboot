package com.fragment.policeforce.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.admin.dto.StationHeadcountUpdateDTO;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.DutyModule;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.StationModuleConfig;
import com.fragment.policeforce.efficiency.service.EfficiencyConstants;
import com.fragment.policeforce.efficiency.service.RecalculationService;
import com.fragment.policeforce.mapper.DutyModuleMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.mapper.StationModuleConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 警局配置人数管理服务 —— 对应 read.txt 中
 * 「警局配置人数的展示界面」与「配置人数的详情展示页面」的数据维护。
 *
 * <p>配置人数是效能公式的输入（模块效能 = 配置人数 × 比重 × 平均个体效能分），
 * 所以保存后必须触发该警局的增量重算。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StationHeadcountAdminService {

    private final StationModuleConfigMapper configMapper;
    private final PoliceStationMapper stationMapper;
    private final DutyModuleMapper moduleMapper;
    private final RecalculationService recalculationService;
    private final AdminAuditHelper adminAuditHelper;

    /**
     * 查询某警局的配置人数明细。
     */
    public List<StationModuleConfig> listByStation(Long stationId) {
        return configMapper.selectList(new LambdaQueryWrapper<StationModuleConfig>()
                .eq(StationModuleConfig::getStationId, stationId)
                .orderByAsc(StationModuleConfig::getModuleId));
    }

    /**
     * 批量设置某警局的配置人数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(StationHeadcountUpdateDTO dto) {
        PoliceStation station = stationMapper.selectById(dto.getStationId());
        if (station == null) {
            throw new BusinessException("警局不存在");
        }

        adminAuditHelper.execute(
                "STATION_MODULE_CONFIG",
                "UPDATE",
                "POLICE_STATION",
                () -> dto.getStationId(),
                () -> "stationId=" + dto.getStationId() + ", items=" + dto.getItems().size(),
                () -> {
                    Set<Long> moduleIds = new HashSet<>();
                    for (StationHeadcountUpdateDTO.Item item : dto.getItems()) {
                        if (!moduleIds.add(item.getModuleId())) {
                            throw new BusinessException(400, "配置中存在重复的模块");
                        }
                        DutyModule module = moduleMapper.selectById(item.getModuleId());
                        if (module == null) {
                            throw new BusinessException(400, "勤务模块不存在：" + item.getModuleId());
                        }
                        Integer headcount = item.getConfiguredHeadcount();
                        if (headcount == null || headcount < 0) {
                            throw new BusinessException(400, "配置人数不能为负");
                        }

                        StationModuleConfig config = configMapper.selectOne(
                                new LambdaQueryWrapper<StationModuleConfig>()
                                        .eq(StationModuleConfig::getStationId, dto.getStationId())
                                        .eq(StationModuleConfig::getModuleId, item.getModuleId())
                                        .last("LIMIT 1"));
                        if (config == null) {
                            config = new StationModuleConfig();
                            config.setStationId(dto.getStationId());
                            config.setModuleId(item.getModuleId());
                            config.setConfiguredHeadcount(headcount);
                            config.setCreatedAt(LocalDateTime.now());
                            config.setUpdatedAt(LocalDateTime.now());
                            if (configMapper.insert(config) <= 0) {
                                throw new BusinessException("新增配置人数失败");
                            }
                        } else {
                            config.setConfiguredHeadcount(headcount);
                            config.setUpdatedAt(LocalDateTime.now());
                            if (configMapper.updateById(config) <= 0) {
                                throw new BusinessException("更新配置人数失败");
                            }
                        }
                    }
                }
        );

        // 配置人数变了 → 该警局增量重算（异步）。
        try {
            recalculationService.trigger(
                    EfficiencyConstants.TRIGGER_HEADCOUNT,
                    EfficiencyConstants.SCOPE_STATION,
                    dto.getStationId(),
                    null,
                    null);
        } catch (RuntimeException exception) {
            log.warn("Failed to trigger recalculation after headcount change. stationId={}, reason={}",
                    dto.getStationId(), exception.getMessage());
        }
    }
}
