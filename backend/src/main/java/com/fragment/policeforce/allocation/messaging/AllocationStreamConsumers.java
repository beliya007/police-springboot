package com.fragment.policeforce.allocation.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fragment.policeforce.allocation.service.AllocationConfirmationService;
import com.fragment.policeforce.allocation.service.AllocationResultService;
import com.fragment.policeforce.common.outbox.MessageOutboxEnvelope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 警力调配命令与结果事件的消息消费入口。
 *
 * <p>Bean 名必须与 {@code application.yml} 里 {@code spring.cloud.function.definition}
 * 声明的函数名一致，否则启动时会报「找不到函数」。</p>
 */
@Configuration
public class AllocationStreamConsumers {

    /**
     * 消费「调配命令」：确认热门名额的最终归属。
     */
    @Bean
    Consumer<AllocationCreateCommand> allocationCommandConsumer(
            AllocationConfirmationService confirmationService) {
        return confirmationService::confirm;
    }

    /**
     * 消费「结果事件」：收敛 Redis 状态 + 发站内通知 + 建立提醒与自动取消任务。
     *
     * <p>消息体是 Outbox 信封，真正的业务事件在 {@code envelope.getPayload()} 里，
     * 因此要先反序列化再交给结果服务。</p>
     */
    @Bean
    Consumer<Message<MessageOutboxEnvelope>> allocationResultConsumer(
            ObjectMapper objectMapper,
            AllocationResultService resultService) {
        return message -> {
            MessageOutboxEnvelope envelope = message.getPayload();
            try {
                AllocationResultEvent event =
                        objectMapper.readValue(envelope.getPayload(), AllocationResultEvent.class);
                resultService.handle(envelope.getEventId(), event);
            } catch (JsonProcessingException exception) {
                // 信封内容解析不了 → 数据有问题，抛异常让 MQ 重投。
                throw new IllegalArgumentException("Invalid allocation result payload", exception);
            }
        };
    }
}
