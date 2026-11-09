package com.fragment.policeforce.allocation.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.redis.RedisDecisionUnknownException;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationTransactionListenerTest {

    private static final String REQUEST_ID = "33c8aa68-9fe0-4d30-afd5-6e62de86bd6c";

    @Mock HotAllocationRedisService hotRedis;

    private ObjectMapper objectMapper;
    private AllocationTransactionListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new AllocationTransactionListener(hotRedis, objectMapper);
    }

    @Test
    void acceptedRedisTransactionShouldCommitHalfMessage() {
        AllocationCreateCommand command = command();
        var state = state(command);
        var context = new AllocationCommandPublisher.LocalTransactionContext(command);
        when(hotRedis.accept(eq(REQUEST_ID), eq(7L), eq(101L), eq(1L), eq(10L)))
                .thenReturn(state);

        LocalTransactionState decision = listener.executeLocalTransaction(message(command), context);

        assertThat(decision).isEqualTo(LocalTransactionState.COMMIT_MESSAGE);
        assertThat(context.result()).isSameAs(state);
    }

    @Test
    void deterministicBusinessRejectionShouldRollbackHalfMessage() {
        AllocationCreateCommand command = command();
        var context = new AllocationCommandPublisher.LocalTransactionContext(command);
        when(hotRedis.accept(eq(REQUEST_ID), eq(7L), eq(101L), eq(1L), eq(10L)))
                .thenThrow(new BusinessException(409, "热门时段余量不足"));

        LocalTransactionState decision = listener.executeLocalTransaction(message(command), context);

        assertThat(decision).isEqualTo(LocalTransactionState.ROLLBACK_MESSAGE);
        assertThatThrownBy(context::result)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));
    }

    @Test
    void unavailableRedisShouldLeaveTransactionForBrokerCheck() {
        AllocationCreateCommand command = command();
        var context = new AllocationCommandPublisher.LocalTransactionContext(command);
        when(hotRedis.accept(eq(REQUEST_ID), eq(7L), eq(101L), eq(1L), eq(10L)))
                .thenThrow(new RedisDecisionUnknownException(new IllegalStateException("Redis unavailable")));

        assertThat(listener.executeLocalTransaction(message(command), context))
                .isEqualTo(LocalTransactionState.UNKNOW);
        assertThatThrownBy(context::result)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相同 Idempotency-Key");
    }

    @Test
    void deterministicUnavailableStateShouldRollbackInsteadOfWaitingForBrokerCheck() {
        AllocationCreateCommand command = command();
        var context = new AllocationCommandPublisher.LocalTransactionContext(command);
        when(hotRedis.accept(eq(REQUEST_ID), eq(7L), eq(101L), eq(1L), eq(10L)))
                .thenThrow(new BusinessException(503, "热门预约通道暂时不可用，请稍后重试"));

        assertThat(listener.executeLocalTransaction(message(command), context))
                .isEqualTo(LocalTransactionState.ROLLBACK_MESSAGE);
        assertThatThrownBy(context::result)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(503));
    }

    @Test
    void brokerCheckShouldCommitOnlyMatchingRedisRequest() {
        AllocationCreateCommand command = command();
        MessageExt message = checkMessage(command);
        when(hotRedis.getRequest(REQUEST_ID)).thenReturn(state(command));

        assertThat(listener.checkLocalTransaction(message))
                .isEqualTo(LocalTransactionState.COMMIT_MESSAGE);

        when(hotRedis.getRequest(REQUEST_ID)).thenReturn(null);
        assertThat(listener.checkLocalTransaction(message))
                .isEqualTo(LocalTransactionState.ROLLBACK_MESSAGE);
    }

    @Test
    void brokerCheckShouldStayUnknownWhileRedisCannotBeRead() {
        when(hotRedis.getRequest(REQUEST_ID)).thenThrow(new IllegalStateException("network timeout"));

        assertThat(listener.checkLocalTransaction(checkMessage(command())))
                .isEqualTo(LocalTransactionState.UNKNOW);
    }

    @Test
    void brokerCheckShouldRollbackWhenRequestIdParametersDoNotMatch() {
        AllocationCreateCommand command = command();
        when(hotRedis.getRequest(REQUEST_ID)).thenReturn(new HotAllocationRedisService.HotRequestState(
                REQUEST_ID, 7L, 101L, 99L, 10L, "PRE_RESERVED",
                null, null, null, null));

        assertThat(listener.checkLocalTransaction(checkMessage(command)))
                .isEqualTo(LocalTransactionState.ROLLBACK_MESSAGE);
    }

    private AllocationCreateCommand command() {
        return new AllocationCreateCommand(
                REQUEST_ID, 7L, 101L, 1L, 10L);
    }

    private HotAllocationRedisService.HotRequestState state(AllocationCreateCommand command) {
        return new HotAllocationRedisService.HotRequestState(
                command.requestId(), command.userId(), command.officerId(),
                command.stationId(), command.slotId(),
                "PRE_RESERVED", null, null, null, null);
    }

    private Message message(AllocationCreateCommand command) {
        try {
            return new Message("allocation-command", objectMapper.writeValueAsBytes(command));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private MessageExt checkMessage(AllocationCreateCommand command) {
        MessageExt message = new MessageExt();
        message.setKeys(command.requestId());
        message.setBody(message(command).getBody());
        return message;
    }
}
