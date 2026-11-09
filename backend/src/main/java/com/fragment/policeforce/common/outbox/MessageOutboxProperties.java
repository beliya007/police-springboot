package com.fragment.policeforce.common.outbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.message-outbox")
public class MessageOutboxProperties {

    private boolean enabled = false;
    private String producerGroup = "police-force-message-outbox-producer-group";
    private int maxReconsumeTimes = -1;
    private String delayTopic = "allocation-delay";
    private String delayConsumerGroup = "police-force-delay-consumer-group";
    private int lockSeconds = 60;
    private Outbox outbox = new Outbox();

    @Data
    public static class Outbox {
        private long dispatchDelayMillis = 1000L;
        private int batchSize = 20;
        private int maxRetryCount = 10;
        private long initialRetryDelayMillis = 1000L;
        private long maxRetryDelayMillis = 300000L;
    }
}
