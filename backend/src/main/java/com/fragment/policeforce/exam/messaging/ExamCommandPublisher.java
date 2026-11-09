package com.fragment.policeforce.exam.messaging;

import com.alibaba.cloud.stream.binder.rocketmq.constant.RocketMQConst;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.exam.redis.ExamRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 体检预约命令发布器。
 *
 * <p>把体检预约作为 RocketMQ 事务半消息发出：半消息在 broker 侧不可见，
 * 只有本地事务（Redis Lua 原子预占）返回 COMMIT_MESSAGE 后才真正投递到消费端。
 * 这样「名额预占」与「预约落库」之间不会出现中间态：
 * Redis 预占失败 → 半消息回滚，消费端根本收不到消息。</p>
 */
@Component
@RequiredArgsConstructor
public class ExamCommandPublisher {

    public static final String OUTPUT_BINDING = "examCommand-out-0";
    public static final String TAG_CREATE = "CREATE";

    private final StreamBridge streamBridge;

    /**
     * 发送一条体检预约事务消息，并返回 Redis 预占的结果。
     */
    public ExamRedisService.ExamRequestState publish(ExamCreateCommand command) {
        LocalTransactionContext transaction = new LocalTransactionContext(command);
        try {
            boolean accepted = streamBridge.send(
                    OUTPUT_BINDING,
                    MessageBuilder.withPayload(command)
                            .setHeader(RocketMQConst.Headers.TAGS, TAG_CREATE)
                            .setHeader(RocketMQConst.Headers.KEYS, command.requestId())
                            .setHeader(RocketMQConst.USER_TRANSACTIONAL_ARGS, transaction)
                            .build()
            );
            if (!accepted) {
                throw unavailable(null);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        return transaction.result();
    }

    private BusinessException unavailable(Throwable cause) {
        BusinessException exception = new BusinessException(503, "体检预约消息暂时无法受理，请稍后重试");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    /**
     * 本地事务上下文：在「当前进程内」传递 Redis 预占的结论。
     */
    static final class LocalTransactionContext {
        private final ExamCreateCommand command;
        private ExamRedisService.ExamRequestState state;
        private BusinessException rejection;

        LocalTransactionContext(ExamCreateCommand command) {
            this.command = command;
        }

        ExamCreateCommand command() {
            return command;
        }

        void commit(ExamRedisService.ExamRequestState state) {
            this.state = state;
        }

        void rollback(BusinessException rejection) {
            this.rejection = rejection;
        }

        ExamRedisService.ExamRequestState result() {
            if (state != null) {
                return state;
            }
            if (rejection != null) {
                throw rejection;
            }
            throw new BusinessException(503, "体检预约事务状态正在由消息队列确认，请使用相同 Idempotency-Key 重试");
        }
    }
}
