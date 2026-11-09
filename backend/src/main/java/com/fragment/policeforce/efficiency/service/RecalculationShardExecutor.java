package com.fragment.policeforce.efficiency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.entity.DutyModule;
import com.fragment.policeforce.entity.EfficiencyFactor;
import com.fragment.policeforce.entity.EfficiencyResult;
import com.fragment.policeforce.entity.ExamResult;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.entity.StationEfficiency;
import com.fragment.policeforce.entity.StationModuleConfig;
import com.fragment.policeforce.efficiency.messaging.RecalcShardCommand;
import com.fragment.policeforce.efficiency.persistence.EfficiencyFactorMapper;
import com.fragment.policeforce.efficiency.persistence.EfficiencyResultMapper;
import com.fragment.policeforce.efficiency.persistence.RecalculationShardMapper;
import com.fragment.policeforce.efficiency.persistence.StationEfficiencyMapper;
import com.fragment.policeforce.mapper.DutyModuleMapper;
import com.fragment.policeforce.mapper.ExamResultMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import com.fragment.policeforce.mapper.StationModuleConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分片计算执行器 —— 批量重算的实际计算入口。
 *
 * <p>这个类承载了本项目「消除读放大」的全部实现细节：
 * <ol>
 *   <li><strong>读放大被消除</strong>：一个分片内只做 4 次批量查询
 *       （配置人数 / 警员 / 体检结论 / 因素权重），
 *       而不是「每个警局查一次」。分片内所有警局在内存里一次算完。</li>
 *   <li><strong>结果覆盖用乐观 CAS</strong>：只有更大的 calc_version 才能覆盖
 *       {@code station_efficiency.latest_calc_version}，
 *       杜绝「旧权重 + 新人数」的错误组合。</li>
 *   <li><strong>幂等靠分片状态机</strong>：{@code claimIfPending} 只让 PENDING 的分片
 *       被执行一次，重复投递直接跳过。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecalculationShardExecutor {

    private final RecalculationShardMapper shardMapper;
    private final StationEfficiencyMapper stationEfficiencyMapper;
    private final EfficiencyResultMapper efficiencyResultMapper;
    private final EfficiencyFactorMapper factorMapper;
    private final StationModuleConfigMapper configMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamResultMapper examResultMapper;
    private final DutyModuleMapper moduleMapper;

    /**
     * 执行一个分片。
     *
     * @return 本分片实际更新的警局数
     */
    @Transactional(rollbackFor = Exception.class)
    public int execute(RecalcShardCommand command) {
        if (command == null || command.stationIds() == null || command.stationIds().isEmpty()) {
            return 0;
        }

        // 幂等第一道：分片状态机抢占。重复投递拿不到抢占权，直接返回。
        int claimed = shardMapper.claimIfPending(command.batchNo(), command.shardIndex());
        if (claimed <= 0) {
            log.info("Recalculation shard already claimed, skip. batchNo={}, shardIndex={}",
                    command.batchNo(), command.shardIndex());
            return 0;
        }

        try {
            int updated = computeShard(command);
            shardMapper.markDone(command.batchNo(), command.shardIndex());
            return updated;
        } catch (RuntimeException exception) {
            // 失败时把分片放回 PENDING，等 MQ 重投；shard 的 retry_count 记录重试次数。
            shardMapper.markFailed(command.batchNo(), command.shardIndex(), exception.getMessage());
            throw exception;
        }
    }

    /**
     * 分片内的实际计算。
     */
    private int computeShard(RecalcShardCommand command) {
        List<Long> stationIds = command.stationIds();

        // ---- 读放大控制点：整个分片只有这几次批量查询 ----
        List<StationModuleConfig> configs = configMapper.selectByStationIds(stationIds);
        List<PoliceOfficer> officers = officerMapper.selectActiveByStationIds(stationIds);
        List<EfficiencyFactor> factors = factorMapper.selectEnabled();

        if (factors.isEmpty()) {
            throw new IllegalStateException("未配置效能因素，无法计算");
        }
        List<EfficiencyFactor> ageFactors = factors.stream()
                .filter(factor -> EfficiencyConstants.FACTOR_TYPE_AGE.equals(factor.getFactorType()))
                .toList();
        List<EfficiencyFactor> genderFactors = factors.stream()
                .filter(factor -> EfficiencyConstants.FACTOR_TYPE_GENDER.equals(factor.getFactorType()))
                .toList();

        // 体检结论：一次查回全部分片警员的最新结论
        List<Long> officerIds = officers.stream().map(PoliceOfficer::getId).toList();
        Map<Long, ExamResult> conclusions = new HashMap<>();
        if (!officerIds.isEmpty()) {
            for (ExamResult result : examResultMapper.selectLatestResults(officerIds)) {
                conclusions.put(result.getOfficerId(), result);
            }
        }

        // 模块比重：一次查回所有模块
        Map<Long, DutyModule> modules = new HashMap<>();
        for (DutyModule module : moduleMapper.selectEnabled()) {
            modules.put(module.getId(), module);
        }

        // ---- 在内存里按 (stationId, moduleId) 分组 ----
        Map<Long, Map<Long, StationModuleConfig>> configIndex = new HashMap<>();
        for (StationModuleConfig config : configs) {
            configIndex.computeIfAbsent(config.getStationId(), key -> new HashMap<>())
                    .put(config.getModuleId(), config);
        }
        Map<Long, Map<Long, List<PoliceOfficer>>> officerIndex = new HashMap<>();
        for (PoliceOfficer officer : officers) {
            officerIndex.computeIfAbsent(officer.getStationId(), key -> new HashMap<>())
                    .computeIfAbsent(officer.getModuleId(), key -> new ArrayList<>())
                    .add(officer);
        }

        LocalDate today = LocalDate.now();
        int updatedCount = 0;

        for (Long stationId : stationIds) {
            Map<Long, StationModuleConfig> stationConfigs = configIndex.get(stationId);
            if (stationConfigs == null || stationConfigs.isEmpty()) {
                continue;
            }
            Map<Long, List<PoliceOfficer>> stationOfficers = officerIndex.get(stationId);

            double stationScore = 0.0d;
            int configuredTotal = 0;
            int effectiveTotal = 0;

            for (Map.Entry<Long, StationModuleConfig> entry : stationConfigs.entrySet()) {
                Long moduleId = entry.getKey();
                StationModuleConfig config = entry.getValue();
                DutyModule module = modules.get(moduleId);
                if (module == null) {
                    continue;
                }
                int configured = config.getConfiguredHeadcount() == null ? 0 : config.getConfiguredHeadcount();
                int ratio = module.getRatio() == null ? 0 : module.getRatio();

                List<PoliceOfficer> moduleOfficers = stationOfficers == null
                        ? List.of() : stationOfficers.getOrDefault(moduleId, List.of());

                // 模块内平均个体效能分：只有计入有效警力的警员参与平均
                double scoreSum = 0.0d;
                int effective = 0;
                for (PoliceOfficer officer : moduleOfficers) {
                    ExamResult result = conclusions.get(officer.getId());
                    String conclusion = result == null ? null : result.getConclusion();
                    if (!EfficiencyCalcEngine.countsAsEffectiveHeadcount(conclusion)) {
                        // 体检不合格：不计入有效警力
                        continue;
                    }
                    effective++;
                    int age = EfficiencyCalcEngine.ageOf(officer.getBirthDate().toLocalDate(), today);
                    scoreSum += EfficiencyCalcEngine.officerScore(
                            age, officer.getGender(), ageFactors, genderFactors, conclusion);
                }
                double averageScore = effective == 0 ? 0.0d : EfficiencyCalcEngine.round(scoreSum / effective);
                double moduleScore = EfficiencyCalcEngine.moduleScore(configured, ratio, averageScore);

                configuredTotal += configured;
                effectiveTotal += effective;
                stationScore += moduleScore;

                // 写结果快照（同一 station+module+version 唯一，重复执行幂等）
                efficiencyResultMapper.insert(upsertSnapshot(
                        stationId, moduleId, command, configured, effective, averageScore, moduleScore, ratio));
            }

            // 乐观 CAS 收敛：只有更大的 calc_version 才覆盖
            stationEfficiencyMapper.insertIfAbsent(stationId);
            int casUpdated = stationEfficiencyMapper.casUpdateLatest(
                    stationId,
                    command.calcVersion(),
                    EfficiencyCalcEngine.round(stationScore),
                    configuredTotal,
                    effectiveTotal,
                    EfficiencyCalcEngine.actualEffectiveness(effectiveTotal, configuredTotal),
                    EfficiencyCalcEngine.shortageHeadcount(configuredTotal, effectiveTotal));
            if (casUpdated > 0) {
                updatedCount++;
            } else {
                log.info("Station efficiency skipped by version CAS. stationId={}, calcVersion={}",
                        stationId, command.calcVersion());
            }
        }

        return updatedCount;
    }

    /**
     * 构造结果快照。同一 (station, module, version) 只应有一条，
     * 重复执行时先删后插保证幂等。
     */
    private EfficiencyResult upsertSnapshot(Long stationId,
                                            Long moduleId,
                                            RecalcShardCommand command,
                                            int configured,
                                            int effective,
                                            double averageScore,
                                            double moduleScore,
                                            int ratio) {
        EfficiencyResult existing = efficiencyResultMapper.selectOne(
                new LambdaQueryWrapper<EfficiencyResult>()
                        .eq(EfficiencyResult::getStationId, stationId)
                        .eq(EfficiencyResult::getModuleId, moduleId)
                        .eq(EfficiencyResult::getCalcVersion, command.calcVersion())
                        .last("LIMIT 1"));
        if (existing != null) {
            efficiencyResultMapper.deleteById(existing.getId());
        }

        EfficiencyResult result = new EfficiencyResult();
        result.setStationId(stationId);
        result.setModuleId(moduleId);
        result.setCalcVersion(command.calcVersion());
        result.setBatchNo(command.batchNo());
        result.setConfiguredHeadcount(configured);
        result.setEffectiveHeadcount(effective);
        result.setAverageOfficerScore(averageScore);
        result.setModuleScore(moduleScore);
        result.setModuleRatio(ratio);
        return result;
    }
}
