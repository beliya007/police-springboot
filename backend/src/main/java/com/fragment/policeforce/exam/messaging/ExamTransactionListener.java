package com.fragment.policeforce.exam.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.exam.redis.ExamRedisService;
import com.fragment.policeforce.exam.redis.RedisDecisionUnknownException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * RocketMQ 事务消息监听器：把「Redis 原子预占」变成半消息的本地事务。
 *
 * <p>这是体检链路防止超卖的关键：名额扣减在 Redis Lua 里原子完成，
 * 只有扣成功才提交半消息；扣失败（名额已满 / 一人一档冲突）直接回滚半消息，
 * 消费端根本不会收到这条消息，MySQL 侧也就不会有任何写入压力。</p>
 */
@Component("examTransactionListener")
@RequiredArgsConstructor
@Slf4j
public class ExamTransactionListener implements TransactionListener {

    private final ExamRedisService examRedis;
    private final ObjectMapper objectMapper;

    @Override
    public LocalTransactionState executeLocalTransaction(Message message, Object argument) {
        ExamCommandPublisher.LocalTransactionContext context = localContext(argument);
        if (context == null) {
            log.error("Exam transaction is missing its local context");
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        try {
            ExamCreateCommand command = context.command();
            ExamRedisService.ExamRequestState state = examRedis.accept(
                    command.requestId(), command.officerId(), command.slotId(), command.batchId());
            context.commit(state);
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (RedisDecisionUnknownException exception) {
            log.warn("Redis decision is unknown for exam transaction: {}", exception.getMessage());
            return LocalTransactionState.UNKNOW;
        } catch (BusinessException exception) {
            // Lua 已给出明确拒绝结论：回滚半消息，并把原因返回给请求线程。
            context.rollback(exception);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (RuntimeException exception) {
            log.warn("Unable to execute exam Redis transaction", exception);
            return LocalTransactionState.UNKNOW;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt message) {
        String requestId = message.getKeys();
        if (!StringUtils.hasText(requestId)) {
            log.error("Exam transaction message has no requestId key");
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        try {
            ExamCreateCommand command = objectMapper.readValue(message.getBody(), ExamCreateCommand.class);
            ExamRedisService.ExamRequestState state = examRedis.getRequest(requestId);
            return state != null && matches(command, state)
                    ? LocalTransactionState.COMMIT_MESSAGE
                    : LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (IOException exception) {
            log.error("Rejecting malformed exam transaction message. requestId={}", requestId, exception);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (RuntimeException exception) {
            log.warn("Exam transaction check cannot reach Redis. requestId={}, reason={}",
                    requestId, exception.getMessage());
            return LocalTransactionState.UNKNOW;
        }
    }

    private ExamCommandPublisher.LocalTransactionContext localContext(Object argument) {
        return argument instanceof ExamCommandPublisher.LocalTransactionContext context ? context : null;
    }

    private boolean matches(ExamCreateCommand command, ExamRedisService.ExamRequestState state) {
        return command.requestId().equals(state.requestId())
                && command.officerId().equals(state.officerId())
                && command.slotId().equals(state.slotId())
                && command.batchId().equals(state.batchId());
    }
}
