package com.fragment.policeforce.common.outbox;

import com.alibaba.cloud.stream.binder.rocketmq.constant.RocketMQConst;
import com.fragment.policeforce.entity.MessageOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The only transport adapter between the transactional outbox and RocketMQ.
 *
 * <p>每个业务 topic 对应一个 output binding。这里按 topic 路由，
 * 新增业务线时只要加一个 binding 和一个分支即可。</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxStreamPublisher {

    static final String DELAY_OUTPUT = "reservationDelay-out-0";
    static final String AUDIT_OUTPUT = "adminAudit-out-0";
    static final String RESERVATION_RESULT_OUTPUT = "reservationResult-out-0";
    static final String EXAM_RESULT_OUTPUT = "examResult-out-0";
    static final String EXAM_WAITING_OUTPUT = "examWaiting-out-0";
    static final String EFFICIENCY_RESULT_OUTPUT = "efficiencyResult-out-0";

    private final StreamBridge streamBridge;
    private final MessageOutboxProperties outboxProperties;

    @Value("${app.audit.mq.topic:admin-audit-log}")
    private String auditTopic;

    @Value("${app.allocation.command.result-topic:allocation-result}")
    private String reservationResultTopic;

    @Value("${app.exam.command.result-topic:exam-result}")
    private String examResultTopic;

    @Value("${app.exam.waiting.topic:exam-waiting}")
    private String examWaitingTopic;

    @Value("${app.efficiency.command.result-topic:efficiency-result}")
    private String efficiencyResultTopic;

    public void publish(MessageOutbox outbox, MessageOutboxEnvelope envelope) {
        if (outbox == null || envelope == null) {
            throw new IllegalArgumentException("Outbox message must not be null");
        }

        MessageBuilder<MessageOutboxEnvelope> builder = MessageBuilder.withPayload(envelope);
        if (StringUtils.hasText(outbox.getTag())) {
            builder.setHeader(RocketMQConst.Headers.TAGS, outbox.getTag());
        }
        if (StringUtils.hasText(outbox.getMessageKey())) {
            builder.setHeader(RocketMQConst.Headers.KEYS, outbox.getMessageKey());
        }
        if (!streamBridge.send(bindingFor(outbox.getTopic()), builder.build())) {
            throw new IllegalStateException("RocketMQ binder rejected outbox event " + outbox.getEventId());
        }
    }

    private String bindingFor(String topic) {
        if (outboxProperties.getDelayTopic().equals(topic)) {
            return DELAY_OUTPUT;
        }
        if (auditTopic.equals(topic)) {
            return AUDIT_OUTPUT;
        }
        if (reservationResultTopic != null && reservationResultTopic.equals(topic)) {
            return RESERVATION_RESULT_OUTPUT;
        }
        if (examResultTopic != null && examResultTopic.equals(topic)) {
            return EXAM_RESULT_OUTPUT;
        }
        if (examWaitingTopic != null && examWaitingTopic.equals(topic)) {
            return EXAM_WAITING_OUTPUT;
        }
        if (efficiencyResultTopic != null && efficiencyResultTopic.equals(topic)) {
            return EFFICIENCY_RESULT_OUTPUT;
        }
        throw new IllegalArgumentException("No RocketMQ output binding for topic: " + topic);
    }
}
