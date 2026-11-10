package com.grupomariposa.orderworker.infrastructure.adapter.outbound.customer;

import com.grupomariposa.orderworker.domain.exception.CustomerNotFoundException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientCustomerAdapterTest {

    private MockWebServer server;
    private WebClientCustomerAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        adapter = new WebClientCustomerAdapter(
                webClient,
                EnrichmentConfig.breaker("customer-test", ResilienceProperties.defaults()),
                EnrichmentConfig.retry("customer-test", ResilienceProperties.defaults()));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void returnsActiveCustomer() {
        server.enqueue(json("""
                {"customerId":"customer-456","name":"Ada","email":"a@b.com","status":"ACTIVE"}"""));

        StepVerifier.create(adapter.fetchCustomer("customer-456"))
                .assertNext(customer -> {
                    assertEquals("customer-456", customer.customerId());
                    assertTrue(customer.isActive());
                })
                .verifyComplete();
    }

    @Test
    void mapsNotFoundToBusinessExceptionAndDoesNotRetry() {
        server.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(adapter.fetchCustomer("ghost"))
                .expectError(CustomerNotFoundException.class)
                .verify();

        assertEquals(1, server.getRequestCount(), "a 404 is a business outcome and must not be retried");
    }

    @Test
    void retriesOnServerErrorThenSucceeds() {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(json("""
                {"customerId":"customer-456","name":"Ada","status":"ACTIVE"}"""));

        StepVerifier.create(adapter.fetchCustomer("customer-456"))
                .assertNext(customer -> assertEquals("customer-456", customer.customerId()))
                .verifyComplete();

        assertEquals(3, server.getRequestCount(), "should retry twice before the success");
    }
}
