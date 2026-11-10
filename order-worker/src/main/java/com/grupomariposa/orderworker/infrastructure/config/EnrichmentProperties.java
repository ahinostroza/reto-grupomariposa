package com.grupomariposa.orderworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Enrichment configuration bound from the {@code enrichment.*} namespace.
 */
@ConfigurationProperties(prefix = "enrichment")
public record EnrichmentProperties(
        String catalogBaseUrl,
        String customerBaseUrl,
        Duration requestTimeout) {

    public EnrichmentProperties {
        if (catalogBaseUrl == null) {
            catalogBaseUrl = "http://localhost:8081";
        }
        if (customerBaseUrl == null) {
            customerBaseUrl = "http://localhost:8082";
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(2);
        }
    }
}
