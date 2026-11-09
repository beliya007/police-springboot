package com.fragment.policeforce.exam.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fragment.policeforce.common.outbox.MessageOutboxEnvelope;
import com.fragment.policeforce.exam.service.ExamConfirmationService;
import com.fragment.policeforce.exam.service.ExamResultService;
import com.fragment.policeforce.exam.service.ExamWaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 体检链路的 Stream 消费入口。
 *
 * <p>Bean 名必须与 {@code application.yml} 中
 * {@code spring.cloud.function.definition} 声明的函数名一致。</p>
 *
 * <p>三个消费者分别对应体检链路的三个阶段：</p>
 * <ol>
 *   <li>{@code examCommandConsumer}：热门预约落库（事务半消息消费端）；</li>
 *   <li>{@code examResultConsumer}：收敛 Redis 状态（Outbox 结果事件消费端）；</li>
 *   <li>{@code examWaitingConsumer}：候补递补（延迟消息消费端）。</li>
 * </ol>
 *
 * <p>后两个消费的是 Outbox 信封（{@link MessageOutboxEnvelope}），
 * 真正的业务事件在 {@code envelope.getPayload()} 里，需要先反序列化。</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ExamStreamConsumers {

    private final ObjectMapper objectMapper;

    /**
     * 消费热门体检预约命令：把 Redis 预占的结果写进 MySQL。
     */
    @Bean
    Consumer<ExamCreateCommand> examCommandConsumer(ExamConfirmationService confirmationService) {
        return confirmationService::confirm;
    }

    /**
     * 消费体检结果事件：把 Redis 请求状态收敛成终态。
     */
    @Bean
    Consumer<Message<MessageOutboxEnvelope>> examResultConsumer(ExamResultService resultService) {
        return message -> {
            MessageOutboxEnvelope envelope = message.getPayload();
            ExamResultEvent event = readPayload(envelope, ExamResultEvent.class);
            resultService.handle(envelope.getEventId(), event);
        };
    }

    /**
     * 消费候补递补消息：名额释放后从候补队列 FIFO 递补。
     */
    @Bean
    Consumer<Message<MessageOutboxEnvelope>> examWaitingConsumer(ExamWaitingService waitingService) {
        return message -> {
            MessageOutboxEnvelope envelope = message.getPayload();
            ExamWaitingPromotionCommand command = readPayload(envelope, ExamWaitingPromotionCommand.class);
            log.info("Exam waiting promotion received. slotId={}, batchId={}",
                    command.slotId(), command.batchId());
            waitingService.promote(command);
        };
    }

    private <T> T readPayload(MessageOutboxEnvelope envelope, Class<T> payloadType) {
        if (envelope == null || envelope.getPayload() == null) {
            throw new IllegalArgumentException("Invalid outbox envelope");
        }
        try {
            return objectMapper.readValue(envelope.getPayload(), payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid " + envelope.getEventType() + " payload", exception);
        }
    }
}
