package com.grupomariposa.orderworker.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.grupomariposa.orderworker.application.port.out.CustomerPort;
import com.grupomariposa.orderworker.application.port.out.ProductCatalogPort;
import com.grupomariposa.orderworker.domain.exception.BusinessValidationException;
import com.grupomariposa.orderworker.domain.product.Product;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.catalog.WebClientProductCatalogAdapter;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.customer.WebClientCustomerAdapter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Composition root for the enrichment adapters. Each downstream service gets its
 * own circuit breaker and retry, so a failure in the catalog never trips the
 * breaker protecting the customer service (bulkheading by dependency). The retry
 * and breaker tunables come from {@link ResilienceProperties}.
 */
@Configuration
@EnableConfigurationProperties({EnrichmentProperties.class, ResilienceProperties.class})
public class EnrichmentConfig {

    @Bean
    ProductCatalogPort productCatalogPort(WebClient.Builder builder,
                                          EnrichmentProperties props,
                                          ResilienceProperties resilience) {
        WebClient client = client(builder, props.catalogBaseUrl(), props.requestTimeout());
        Cache<String, Product> cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        return new WebClientProductCatalogAdapter(
                client, breaker("catalog", resilience), retry("catalog", resilience), cache);
    }

    @Bean
    CustomerPort customerPort(WebClient.Builder builder,
                              EnrichmentProperties props,
                              ResilienceProperties resilience) {
        WebClient client = client(builder, props.customerBaseUrl(), props.requestTimeout());
        return new WebClientCustomerAdapter(
                client, breaker("customer", resilience), retry("customer", resilience));
    }

    private static WebClient client(WebClient.Builder builder, String baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.create().responseTimeout(timeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** Retry only transient failures: business errors and open-breaker calls are ignored. */
    public static Retry retry(String name, ResilienceProperties p) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(p.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        p.retryBackoff(), p.retryBackoffMultiplier(), 0.5))
                .ignoreExceptions(BusinessValidationException.class, CallNotPermittedException.class)
                .build();
        return Retry.of(name, config);
    }

    /** Business failures (e.g. 404) must not count towards opening the breaker. */
    public static CircuitBreaker breaker(String name, ResilienceProperties p) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(p.breakerSlidingWindow())
                .failureRateThreshold(p.breakerFailureRateThreshold())
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(p.breakerSlowCallThreshold())
                .waitDurationInOpenState(p.breakerWaitInOpenState())
                .ignoreExceptions(BusinessValidationException.class)
                .build();
        return CircuitBreaker.of(name, config);
    }
}
