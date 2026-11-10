package com.grupomariposa.orderworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables for the enrichment adapters' retry and circuit breaker, bound from
 * {@code enrichment.resilience.*}. Having these configurable (max attempts, wait
 * time, thresholds) instead of hard-coded is an explicit requirement.
 */
@ConfigurationProperties(prefix = "enrichment.resilience")
public record ResilienceProperties(
        int retryMaxAttempts,
        Duration retryBackoff,
        double retryBackoffMultiplier,
        int breakerSlidingWindow,
        float breakerFailureRateThreshold,
        Duration breakerWaitInOpenState,
        Duration breakerSlowCallThreshold) {

    public ResilienceProperties {
        if (retryMaxAttempts <= 0) {
            retryMaxAttempts = 3;
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofMillis(200);
        }
        if (retryBackoffMultiplier <= 0) {
            retryBackoffMultiplier = 2.0;
        }
        if (breakerSlidingWindow <= 0) {
            breakerSlidingWindow = 20;
        }
        if (breakerFailureRateThreshold <= 0) {
            breakerFailureRateThreshold = 50f;
        }
        if (breakerWaitInOpenState == null) {
            breakerWaitInOpenState = Duration.ofSeconds(10);
        }
        if (breakerSlowCallThreshold == null) {
            breakerSlowCallThreshold = Duration.ofSeconds(2);
        }
    }

    /** Defaults, for use outside the Spring context (e.g. tests). */
    public static ResilienceProperties defaults() {
        return new ResilienceProperties(0, null, 0, 0, 0f, null, null);
    }
}
