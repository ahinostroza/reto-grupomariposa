package com.grupomariposa.orderworker.infrastructure.adapter.outbound.customer;

import com.grupomariposa.orderworker.application.port.out.CustomerPort;
import com.grupomariposa.orderworker.domain.customer.Customer;
import com.grupomariposa.orderworker.domain.customer.CustomerStatus;
import com.grupomariposa.orderworker.domain.exception.CustomerNotFoundException;
import com.grupomariposa.orderworker.domain.exception.TransientEnrichmentException;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.customer.generated.CustomerDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the Go customer API. The response DTO is generated from the customer's
 * OpenAPI contract (contract-first). A 404 is mapped to a non-retryable
 * {@link CustomerNotFoundException} (business outcome); 5xx/timeout become a
 * {@link TransientEnrichmentException} that the retry policy will retry.
 */
public class WebClientCustomerAdapter implements CustomerPort {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public WebClientCustomerAdapter(WebClient webClient, CircuitBreaker circuitBreaker, Retry retry) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public Mono<Customer> fetchCustomer(String customerId) {
        return webClient.get()
                .uri("/v1/customers/{id}", customerId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new CustomerNotFoundException(customerId)))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new TransientEnrichmentException(
                                "customer service returned " + response.statusCode())))
                .bodyToMono(CustomerDto.class)
                .map(this::toDomain)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    private Customer toDomain(CustomerDto dto) {
        return new Customer(dto.getCustomerId(), dto.getName(),
                CustomerStatus.valueOf(dto.getStatus().getValue()));
    }
}
