package com.grupomariposa.orderworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "redisson")
public record RedissonProperties(
        String url,
        Duration lockWaitTime,
        Duration lockLeaseTime,
        Duration failedTtl) {

    public RedissonProperties {
        if (url == null) {
            url = "redis://localhost:6379";
        }
        if (lockWaitTime == null) {
            lockWaitTime = Duration.ofSeconds(3);
        }
        if (lockLeaseTime == null) {
            lockLeaseTime = Duration.ofSeconds(30);
        }
        if (failedTtl == null) {
            failedTtl = Duration.ofDays(7);
        }
    }
}
