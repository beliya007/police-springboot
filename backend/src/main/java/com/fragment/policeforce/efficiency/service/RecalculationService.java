package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.entity.RecalculationRequest;
import com.fragment.policeforce.entity.RecalculationShard;
import com.fragment.policeforce.entity.StationModuleConfig;
import com.fragment.policeforce.efficiency.messaging.RecalcShardCommand;
import com.fragment.policeforce.efficiency.messaging.RecalcShardPublisher;
import com.fragment.policeforce.efficiency.persistence.RecalculationRequestMapper;
import com.fragment.policeforce.efficiency.persistence.RecalculationShardMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import com.fragment.policeforce.mapper.StationModuleConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 效能重算编排服务 —— 对外的触发入口。
 *
 * <p>流程：
 * <ol>
 *   <li>参数预检（同步）：权重和、因素覆盖、配置人数、模块比重 —— 不通过直接 400。</li>
 *   <li>预计算版本号：版本号是全局单调递增的，用于结果覆盖的乐观 CAS。</li>
 *   <li>切分分片：把受影响的警局切成若干分片，落 {@code recalculation_shard}。</li>
 *   <li>发布分片消息：每个分片一条消息，消费端并发执行。</li>
 * </ol>
 *
 * <p>版本号的分配必须<strong>在发布消息之前</strong>完成，否则同一个分片可能拿到
 * 比另一个分片更小的版本号，导致 CAS 收敛时结果被错误丢弃。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecalculationService {

    private final EfficiencyPrecheckService precheckService;
    private final RecalculationShardPlanner shardPlanner;
    private final RecalculationShardMapper shardMapper;
    private final RecalculationRequestMapper requestMapper;
    private final RecalcShardPublisher publisher;
    private final StationModuleConfigMapper configMapper;
    private final PoliceOfficerMapper officerMapper;
    private final MessageOutboxService outboxService;

    /**
     * 计算版本号的发号器。用数据库自增主键分配，避免多实例并发时版本号碰撞。
     */
    private final CalcVersionAllocator versionAllocator;

    @Value("${app.efficiency.command.topic:efficiency-recalc}")
    private String topic;

    /**
     * 触发一次重算。
     *
     * @param triggerSource 触发来源
     * @param scopeType     范围类型
     * @param scopeId       范围目标 ID
     * @param district      区域名（DISTRICT 范围时使用）
     * @param requestId     客户端幂等键，可为空（为空则自动生成）
     */
    @Transactional(rollbackFor = Exception.class)
    public RecalcSubmitVO trigger(String triggerSource,
                                   String scopeType,
                                   Long scopeId,
                                   String district,
                                   String requestId) {
        // ---- 第一道闸门：参数预检 ----
        EfficiencyPrecheckService.PrecheckResult precheck = precheckService.check();
        if (!precheck.valid()) {
            throw new BusinessException(400, precheck.rejectReason());
        }

        String actualRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString() : requestId;

        // 幂等：同一个 request_id 直接返回已有批次
        RecalculationRequest existing = requestMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecalculationRequest>()
                        .eq(RecalculationRequest::getRequestId, actualRequestId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return RecalcSubmitVO.accepted(actualRequestId, existing.getBatchNo(),
                    existing.getCalcVersion(), existing.getStatus());
        }

        // ---- 范围解析与分片切分 ----
        List<RecalculationShardPlanner.ShardPlan> plans =
                shardPlanner.plan(scopeType, scopeId, district);
        if (plans.isEmpty()) {
            throw new BusinessException(400, "重算范围内没有可计算的警局");
        }

        // ---- 预检配置人数与警员覆盖 ----
        List<Long> stationIds = plans.stream()
                .flatMap(plan -> plan.stationIds().stream()).toList();
        List<StationModuleConfig> configs = configMapper.selectByStationIds(stationIds);
        String headcountError = precheckService.checkHeadcounts(configs);
        if (headcountError != null) {
            throw new BusinessException(400, headcountError);
        }
        List<PoliceOfficer> officers = officerMapper.selectActiveByStationIds(stationIds);
        String coverageError = precheckService.checkOfficerCoverage(officers, precheck.ageFactors());
        if (coverageError != null) {
            throw new BusinessException(400, coverageError);
        }

        // ---- 版本号先于消息分配 ----
        String batchNo = "RC" + System.currentTimeMillis();
        Long calcVersion = versionAllocator.next();

        // ---- 落账本与分片 ----
        requestMapper.insertProcessingIgnore(actualRequestId, batchNo, triggerSource,
                scopeType, scopeId, calcVersion);
        for (RecalculationShardPlanner.ShardPlan plan : plans) {
            RecalculationShard shard = new RecalculationShard();
            shard.setBatchNo(batchNo);
            shard.setShardIndex(plan.shardIndex());
            shard.setStationIds(toJson(plan.stationIds()));
            shard.setStationCount(plan.stationIds().size());
            shard.setStatus("PENDING");
            shard.setRetryCount(0);
            shardMapper.insert(shard);
        }

        // ---- 发布分片消息 ----
        for (RecalculationShardPlanner.ShardPlan plan : plans) {
            publisher.publishOrFail(new RecalcShardCommand(
                    batchNo, plan.shardIndex(), plan.stationIds(),
                    calcVersion, triggerSource, scopeType));
        }

        log.info("Recalculation batch dispatched. batchNo={}, shards={}, stations={}, calcVersion={}",
                batchNo, plans.size(), stationIds.size(), calcVersion);

        return RecalcSubmitVO.accepted(actualRequestId, batchNo, calcVersion, "PROCESSING");
    }

    /**
     * 查询重算批次状态。
     */
    public RecalcSubmitVO status(String requestId) {
        RecalculationRequest request = requestMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecalculationRequest>()
                        .eq(RecalculationRequest::getRequestId, requestId)
                        .last("LIMIT 1"));
        if (request == null) {
            throw new BusinessException(404, "重算请求不存在");
        }
        return RecalcSubmitVO.accepted(request.getRequestId(), request.getBatchNo(),
                request.getCalcVersion(), request.getStatus());
    }

    private String toJson(List<Long> ids) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids.get(i));
        }
        return builder.append(']').toString();
    }

    /**
     * 重算受理结果。
     *
     * @param requestId   幂等键
     * @param batchNo     批次号
     * @param calcVersion 计算版本
     * @param status      状态
     */
    public record RecalcSubmitVO(String requestId,
                                  String batchNo,
                                  Long calcVersion,
                                  String status) {

        public static RecalcSubmitVO accepted(String requestId, String batchNo,
                                              Long calcVersion, String status) {
            return new RecalcSubmitVO(requestId, batchNo, calcVersion, status);
        }
    }
}
