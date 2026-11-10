package com.grupomariposa.orderworker.infrastructure.adapter.outbound.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.grupomariposa.orderworker.application.port.out.ProductCatalogPort;
import com.grupomariposa.orderworker.domain.exception.TransientEnrichmentException;
import com.grupomariposa.orderworker.domain.product.Money;
import com.grupomariposa.orderworker.domain.product.Product;
import com.grupomariposa.orderworker.domain.product.ProductLookupResult;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.catalog.generated.BatchGetProductsRequestDto;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.catalog.generated.BatchGetProductsResponseDto;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.catalog.generated.ProductDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Calls the Go product-catalog API. Uses a single batch request (never one call
 * per product), a short-lived Caffeine cache for hot products, and resilience
 * operators (circuit breaker inside, retry outside) over the reactive chain.
 *
 * <p>The request/response DTOs are generated from the catalog's OpenAPI contract
 * (contract-first). Missing products are not an error here: the API returns them
 * in {@code notFound}, and the use case decides the business rule. Only
 * 5xx/timeout failures are transient and retried.
 */
public class WebClientProductCatalogAdapter implements ProductCatalogPort {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Cache<String, Product> cache;

    public WebClientProductCatalogAdapter(WebClient webClient,
                                          CircuitBreaker circuitBreaker,
                                          Retry retry,
                                          Cache<String, Product> cache) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.cache = cache;
    }

    @Override
    public Mono<ProductLookupResult> fetchProducts(List<String> productIds) {
        List<String> distinct = productIds.stream().distinct().toList();

        List<Product> cached = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : distinct) {
            Product hit = cache.getIfPresent(id);
            if (hit != null) {
                cached.add(hit);
            } else {
                missing.add(id);
            }
        }

        if (missing.isEmpty()) {
            return Mono.just(new ProductLookupResult(cached, List.of()));
        }

        return callBatch(missing).map(response -> {
            List<Product> fetched = response.getProducts().stream().map(this::toDomain).toList();
            fetched.forEach(p -> cache.put(p.productId(), p));

            List<Product> found = new ArrayList<>(cached);
            found.addAll(fetched);
            List<String> notFound = response.getNotFound() == null ? List.of() : response.getNotFound();
            return new ProductLookupResult(found, notFound);
        });
    }

    private Mono<BatchGetProductsResponseDto> callBatch(List<String> ids) {
        BatchGetProductsRequestDto request = new BatchGetProductsRequestDto().ids(new LinkedHashSet<>(ids));
        return webClient.post()
                .uri(builder -> builder.path("/v1/products:batchGet").build())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new TransientEnrichmentException(
                                "catalog returned " + response.statusCode())))
                .bodyToMono(BatchGetProductsResponseDto.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    private Product toDomain(ProductDto dto) {
        return new Product(dto.getProductId(), dto.getName(), new Money(dto.getPrice(), dto.getCurrency()));
    }
}
