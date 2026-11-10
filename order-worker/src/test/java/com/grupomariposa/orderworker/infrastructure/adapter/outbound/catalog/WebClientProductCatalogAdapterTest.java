package com.grupomariposa.orderworker.infrastructure.adapter.outbound.catalog;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.grupomariposa.orderworker.infrastructure.config.EnrichmentConfig;
import com.grupomariposa.orderworker.infrastructure.config.ResilienceProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebClientProductCatalogAdapterTest {

    private MockWebServer server;
    private WebClientProductCatalogAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        adapter = new WebClientProductCatalogAdapter(
                webClient,
                EnrichmentConfig.breaker("catalog-test", ResilienceProperties.defaults()),
                EnrichmentConfig.retry("catalog-test", ResilienceProperties.defaults()),
                Caffeine.newBuilder().maximumSize(100).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void returnsFoundProductsAndMissingIds() {
        server.enqueue(json("""
                {"products":[{"productId":"product-789","name":"Laptop","description":"x",
                "price":99900,"currency":"USD","active":true}],"notFound":["ghost"]}"""));

        StepVerifier.create(adapter.fetchProducts(List.of("product-789", "ghost")))
                .assertNext(result -> {
                    assertEquals(1, result.found().size());
                    assertEquals("product-789", result.found().get(0).productId());
                    assertEquals(99900, result.found().get(0).price().amountMinor());
                    assertEquals(List.of("ghost"), result.notFound());
                })
                .verifyComplete();
    }

    @Test
    void retriesOnServerErrorThenSucceeds() {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(json("""
                {"products":[{"productId":"product-789","name":"Laptop","description":"x",
                "price":99900,"currency":"USD","active":true}],"notFound":[]}"""));

        StepVerifier.create(adapter.fetchProducts(List.of("product-789")))
                .assertNext(result -> assertEquals(1, result.found().size()))
                .verifyComplete();

        assertEquals(2, server.getRequestCount(), "should have retried once after the 503");
    }

    @Test
    void servesRepeatedProductFromCacheWithoutHittingTheServer() {
        server.enqueue(json("""
                {"products":[{"productId":"product-789","name":"Laptop","description":"x",
                "price":99900,"currency":"USD","active":true}],"notFound":[]}"""));

        adapter.fetchProducts(List.of("product-789")).block();

        StepVerifier.create(adapter.fetchProducts(List.of("product-789")))
                .assertNext(result -> assertEquals(1, result.found().size()))
                .verifyComplete();

        assertEquals(1, server.getRequestCount(), "second lookup must be served from cache");
    }
}
