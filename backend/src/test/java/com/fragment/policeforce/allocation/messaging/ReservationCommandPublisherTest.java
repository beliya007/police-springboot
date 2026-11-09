package com.fragment.policeforce.allocation.messaging;

import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationCommandPublisherTest {

    @Test
    void publisherShouldReturnLocalTransactionResult() {
        StreamBridge bridge = mock(StreamBridge.class);
        AllocationCommandPublisher publisher = new AllocationCommandPublisher(bridge);
        AllocationCreateCommand command = command();
        HotAllocationRedisService.HotRequestState expected = state(command);
        when(bridge.send(eq(AllocationCommandPublisher.OUTPUT_BINDING),
                org.mockito.ArgumentMatchers.<Message<?>>any())).thenAnswer(invocation -> {
            Message<?> message = invocation.getArgument(1);
            Object argument = message.getHeaders().get("TRANSACTIONAL_ARGS");
            assertThat(argument).isInstanceOf(AllocationCommandPublisher.LocalTransactionContext.class);
            ((AllocationCommandPublisher.LocalTransactionContext) argument).commit(expected);
            return true;
        });

        assertThat(publisher.publish(command)).isSameAs(expected);
    }

    @Test
    void publisherShouldRejectWhenBinderDoesNotAcceptMessage() {
        StreamBridge bridge = mock(StreamBridge.class);
        AllocationCommandPublisher publisher = new AllocationCommandPublisher(bridge);
        when(bridge.send(eq(AllocationCommandPublisher.OUTPUT_BINDING),
                org.mockito.ArgumentMatchers.<Message<?>>any())).thenReturn(false);

        assertThatThrownBy(() -> publisher.publish(command()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(503));
    }

    private AllocationCreateCommand command() {
        return new AllocationCreateCommand(
                "33c8aa68-9fe0-4d30-afd5-6e62de86bd6c",
                7L, 101L, 1L, 10L);
    }

    private HotAllocationRedisService.HotRequestState state(AllocationCreateCommand command) {
        return new HotAllocationRedisService.HotRequestState(
                command.requestId(), command.userId(), command.officerId(),
                command.stationId(), command.slotId(),
                "PRE_RESERVED", null, null, null, null);
    }
}
