package com.grupomariposa.orderworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kafka")
public record KafkaProperties(
        String bootstrapServers,
        String topic,
        String dltTopic,
        String groupId,
        int maxRetries,
        Duration retryBackoff) {

    public KafkaProperties {
        if (bootstrapServers == null) {
            bootstrapServers = "localhost:9092";
        }
        if (topic == null) {
            topic = "orders";
        }
        if (dltTopic == null) {
            dltTopic = topic + ".DLT";
        }
        if (groupId == null) {
            groupId = "order-worker";
        }
        if (maxRetries <= 0) {
            maxRetries = 3;
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofMillis(200);
        }
    }
}
