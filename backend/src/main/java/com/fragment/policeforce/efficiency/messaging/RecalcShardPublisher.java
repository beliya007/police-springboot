package com.fragment.policeforce.efficiency.messaging;

import com.alibaba.cloud.stream.binder.rocketmq.constant.RocketMQConst;
import com.fragment.policeforce.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 效能重算分片命令发布器。
 *
 * <p>与警力调配的「热门名额」通道不同，这里<strong>不用 RocketMQ 事务消息</strong>，
 * 原因是分片命令的幂等性已经由「分片状态机的 CAS 抢占」保证：
 * 消费端执行 {@code claimIfPending} 时，只有 PENDING 的分片才能被抢占，
 * 重复投递的消息会拿到 0 行受影响、直接跳过。</p>
 *
 * <p>这样设计的好处是链路更短：没有 prepare/commit 两次往返，
 * 500 并发批量重算时 broker 压力显著更低；而幂等性由数据库状态机而非消息语义保证，
 * 更容易通过单测证明。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecalcShardPublisher {

    public static final String OUTPUT_BINDING = "recalcShard-out-0";
    public static final String TAG_SHARD = "SHARD";

    private final StreamBridge streamBridge;

    @Value("${app.efficiency.command.topic:efficiency-recalc}")
    private String topic;

    /**
     * 发布一个分片命令。
     *
     * @return 是否被 broker 接受
     */
    public boolean publish(RecalcShardCommand command) {
        if (command == null || command.stationIds() == null || command.stationIds().isEmpty()) {
            return false;
        }
        boolean accepted = streamBridge.send(
                OUTPUT_BINDING,
                MessageBuilder.withPayload(command)
                        .setHeader(RocketMQConst.Headers.TAGS, TAG_SHARD)
                        .setHeader(RocketMQConst.Headers.KEYS, command.batchNo() + "#" + command.shardIndex())
                        .build()
        );
        if (!accepted) {
            log.warn("RocketMQ binder rejected recalc shard command. batchNo={}, shardIndex={}",
                    command.batchNo(), command.shardIndex());
        }
        return accepted;
    }

    /**
     * 发布一个分片命令，失败时抛业务异常（由调用方决定是否回滚整个批次）。
     */
    public void publishOrFail(RecalcShardCommand command) {
        if (!publish(command)) {
            throw new BusinessException(503, "效能重算消息暂时无法受理，请稍后重试");
        }
    }
}
