package com.fragment.policeforce.dashboard.service;

import com.fragment.policeforce.dashboard.persistence.DashboardAggregateMapper;
import com.fragment.policeforce.dashboard.vo.ModuleBreakdownVO;
import com.fragment.policeforce.dashboard.vo.PoliceForceOverviewVO;
import com.fragment.policeforce.dashboard.vo.StationOverviewVO;
import com.fragment.policeforce.efficiency.service.EfficiencyCalcEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板聚合服务 —— 对应 read.txt 的三个展示页面：
 * <ol>
 *   <li>警局配置人数展示界面；</li>
 *   <li>警局实际效能展示；</li>
 *   <li>配置人数详情展示（各模块占比及人数分布）。</li>
 * </ol>
 *
 * <p><strong>读放大控制</strong>：整个看板只用两次聚合查询
 * （一次警局级、一次模块级），在内存里组装。
 * 如果按警局循环查明细，就是 N+1 查询，警局一多看板直接超时。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final DashboardAggregateMapper aggregateMapper;

    /**
     * 警力规划总览。
     */
    public PoliceForceOverviewVO overview() {
        List<StationOverviewVO> stations = listStations(null);

        PoliceForceOverviewVO overview = new PoliceForceOverviewVO();
        overview.setStationCount(stations.size());

        int totalConfigured = 0;
        int totalEffective = 0;
        int totalShortage = 0;
        for (StationOverviewVO station : stations) {
            totalConfigured += station.getConfiguredHeadcount() == null ? 0 : station.getConfiguredHeadcount();
            totalEffective += station.getEffectiveHeadcount() == null ? 0 : station.getEffectiveHeadcount();
            totalShortage += station.getShortageHeadcount() == null ? 0 : station.getShortageHeadcount();
        }
        overview.setTotalConfiguredHeadcount(totalConfigured);
        overview.setTotalEffectiveHeadcount(totalEffective);
        overview.setTotalShortageHeadcount(totalShortage);
        overview.setOverallEffectiveness(
                EfficiencyCalcEngine.actualEffectiveness(totalEffective, totalConfigured));

        // 缺编最严重的警局排在前面 —— 这是「警力合理配置」最需要关注的信号。
        List<StationOverviewVO> ranking = new ArrayList<>(stations);
        ranking.removeIf(station -> station.getShortageHeadcount() == null || station.getShortageHeadcount() <= 0);
        ranking.sort(Comparator.comparing(StationOverviewVO::getShortageHeadcount).reversed());
        overview.setShortageRanking(ranking.size() > 10 ? ranking.subList(0, 10) : ranking);

        return overview;
    }

    /**
     * 各警局的配置人数与实际效能列表。
     *
     * @param district 区域过滤；为空表示全部
     */
    public List<StationOverviewVO> listStations(String district) {
        List<DashboardAggregateMapper.StationAggregateRow> rows =
                aggregateMapper.selectStationAggregates(null);
        List<DashboardAggregateMapper.ModuleBreakdownRow> breakdowns =
                aggregateMapper.selectModuleBreakdowns(null);

        Map<Long, List<DashboardAggregateMapper.ModuleBreakdownRow>> breakdownIndex = new HashMap<>();
        for (DashboardAggregateMapper.ModuleBreakdownRow row : breakdowns) {
            breakdownIndex.computeIfAbsent(row.stationId, key -> new ArrayList<>()).add(row);
        }

        List<StationOverviewVO> result = new ArrayList<>();
        for (DashboardAggregateMapper.StationAggregateRow row : rows) {
            if (district != null && !district.isBlank() && !district.equals(row.district)) {
                continue;
            }
            StationOverviewVO vo = toOverview(row);
            List<DashboardAggregateMapper.ModuleBreakdownRow> stationBreakdowns =
                    breakdownIndex.getOrDefault(row.stationId, List.of());
            vo.setModules(toBreakdowns(stationBreakdowns, row.configuredHeadcount, row.effectiveHeadcount));
            result.add(vo);
        }
        return result;
    }

    /**
     * 单个警局的详情（配置人数 + 实际效能 + 模块分布）。
     */
    public StationOverviewVO detail(Long stationId) {
        List<DashboardAggregateMapper.StationAggregateRow> rows =
                aggregateMapper.selectStationAggregates(List.of(stationId));
        if (rows.isEmpty()) {
            throw new com.fragment.policeforce.common.exception.BusinessException(404, "警局不存在");
        }
        DashboardAggregateMapper.StationAggregateRow row = rows.get(0);
        StationOverviewVO vo = toOverview(row);
        List<DashboardAggregateMapper.ModuleBreakdownRow> breakdowns =
                aggregateMapper.selectModuleBreakdowns(List.of(stationId));
        vo.setModules(toBreakdowns(breakdowns, row.configuredHeadcount, row.effectiveHeadcount));
        return vo;
    }

    private StationOverviewVO toOverview(DashboardAggregateMapper.StationAggregateRow row) {
        StationOverviewVO vo = new StationOverviewVO();
        vo.setStationId(row.stationId);
        vo.setStationCode(row.stationCode);
        vo.setStationName(row.stationName);
        vo.setDistrict(row.district);
        vo.setStationType(row.stationType);
        vo.setConfiguredHeadcount(row.configuredHeadcount);
        vo.setEffectiveHeadcount(row.effectiveHeadcount);
        vo.setStationScore(row.stationScore);
        vo.setActualEffectiveness(row.actualEffectiveness);
        vo.setShortageHeadcount(row.shortageHeadcount);
        vo.setLatestCalcVersion(row.latestCalcVersion);
        return vo;
    }

    private List<ModuleBreakdownVO> toBreakdowns(List<DashboardAggregateMapper.ModuleBreakdownRow> rows,
                                                 Integer stationConfigured,
                                                 Integer stationEffective) {
        List<ModuleBreakdownVO> modules = new ArrayList<>();
        int totalConfigured = stationConfigured == null ? 0 : stationConfigured;
        int totalEffective = stationEffective == null ? 0 : stationEffective;

        for (DashboardAggregateMapper.ModuleBreakdownRow row : rows) {
            ModuleBreakdownVO vo = new ModuleBreakdownVO();
            vo.setModuleId(row.moduleId);
            vo.setModuleCode(row.moduleCode);
            vo.setModuleName(row.moduleName);
            vo.setRatio(row.ratio);
            vo.setPhysicalRequired(row.physicalRequired != null && row.physicalRequired == 1);
            vo.setConfiguredHeadcount(row.configuredHeadcount);
            vo.setEffectiveHeadcount(row.effectiveHeadcount);
            vo.setShortageHeadcount(EfficiencyCalcEngine.shortageHeadcount(
                    row.configuredHeadcount == null ? 0 : row.configuredHeadcount,
                    row.effectiveHeadcount == null ? 0 : row.effectiveHeadcount));
            vo.setAverageOfficerScore(row.averageOfficerScore);
            vo.setModuleScore(row.moduleScore);
            vo.setHeadcountShare(share(row.configuredHeadcount, totalConfigured));
            vo.setEffectiveShare(share(row.effectiveHeadcount, totalEffective));
            modules.add(vo);
        }
        return modules;
    }

    private Double share(Integer part, int total) {
        if (part == null || total <= 0) {
            return 0.0d;
        }
        return EfficiencyCalcEngine.round(part * 100.0d / total);
    }
}
