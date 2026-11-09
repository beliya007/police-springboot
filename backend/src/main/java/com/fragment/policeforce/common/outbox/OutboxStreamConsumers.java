package com.fragment.policeforce.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fragment.policeforce.common.audit.AdminAuditLogEvent;
import com.fragment.policeforce.allocation.reminder.messaging.DelayMessageEventTypes;
import com.fragment.policeforce.allocation.reminder.messaging.AllocationAutoCancelDelayPayload;
import com.fragment.policeforce.allocation.reminder.messaging.AllocationReminderDelayPayload;
import com.fragment.policeforce.allocation.reminder.AllocationReminderDeliveryService;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.mapper.AdminAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

/** Function beans keep consumer lifecycle and retry policy in the RocketMQ binder. */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OutboxStreamConsumers {

    private final ObjectMapper objectMapper;
    private final AllocationReminderDeliveryService reminderDeliveryService;
    private final AllocationAutoCancelService reservationAutoCancelService;
    private final AdminAuditLogMapper adminAuditLogMapper;

    @Bean
    Consumer<Message<MessageOutboxEnvelope>> reservationDelayConsumer() {
        return message -> dispatchDelay(envelope(message));
    }

    @Bean
    Consumer<Message<MessageOutboxEnvelope>> adminAuditConsumer() {
        return message -> {
            AdminAuditLogEvent event = payload(message, AdminAuditLogEvent.class);
            try {
                adminAuditLogMapper.insert(event.toLog());
            } catch (DuplicateKeyException duplicate) {
                log.info("Admin audit event already consumed. eventId={}", event.getEventId());
            }
        };
    }

    private void dispatchDelay(MessageOutboxEnvelope envelope) {
        switch (envelope.getEventType()) {
            case DelayMessageEventTypes.RESERVATION_REMINDER -> reminderDeliveryService.deliver(
                    readPayload(envelope, AllocationReminderDelayPayload.class).getReminderTaskId());
            case DelayMessageEventTypes.RESERVATION_AUTO_CANCEL -> reservationAutoCancelService.autoCancel(
                    readPayload(envelope, AllocationAutoCancelDelayPayload.class).getAllocationId());
            default -> throw new IllegalArgumentException("Unsupported delay event type: " + envelope.getEventType());
        }
    }

    private MessageOutboxEnvelope envelope(Message<MessageOutboxEnvelope> message) {
        MessageOutboxEnvelope envelope = message == null ? null : message.getPayload();
        if (envelope == null || !StringUtils.hasText(envelope.getEventId())
                || !StringUtils.hasText(envelope.getEventType()) || !StringUtils.hasText(envelope.getPayload())) {
            throw new IllegalArgumentException("Invalid outbox envelope");
        }
        return envelope;
    }

    private <T> T payload(Message<MessageOutboxEnvelope> message, Class<T> payloadType) {
        return readPayload(envelope(message), payloadType);
    }

    private <T> T readPayload(MessageOutboxEnvelope envelope, Class<T> payloadType) {
        try {
            return objectMapper.readValue(envelope.getPayload(), payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid " + envelope.getEventType() + " payload", exception);
        }
    }
}
