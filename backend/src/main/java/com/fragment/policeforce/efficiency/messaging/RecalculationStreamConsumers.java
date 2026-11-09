package com.fragment.policeforce.efficiency.messaging;

import com.fragment.policeforce.efficiency.service.RecalculationShardExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * 效能重算分片命令的消费入口。
 *
 * <p>幂等策略：<strong>不在消费端做「查账本再判断」</strong>，而是靠分片状态机的
 * CAS 抢占（{@code claimIfPending}）。这样重复投递的代价只是一次注定失败的条件更新，
 * 而不会产生额外的读，也不会有并发窗口。</p>
 *
 * <p>异常处理：执行失败时 {@link RecalculationShardExecutor} 会把分片放回 PENDING，
 * 这里把异常继续抛出，让 RocketMQ 按 {@code max-reconsume-times} 重投。</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RecalculationStreamConsumers {

    /**
     * 消费重算分片命令。
     */
    @Bean
    Consumer<RecalcShardCommand> recalcShardConsumer(RecalculationShardExecutor executor) {
        return command -> {
            log.info("Recalculation shard received. batchNo={}, shardIndex={}, stations={}, calcVersion={}",
                    command.batchNo(), command.shardIndex(), command.stationIds().size(), command.calcVersion());
            executor.execute(command);
        };
    }
}
