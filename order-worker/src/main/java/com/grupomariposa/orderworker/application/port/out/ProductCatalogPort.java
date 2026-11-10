package com.grupomariposa.orderworker.application.port.out;

import com.grupomariposa.orderworker.domain.product.ProductLookupResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Outbound port to the product catalog. Implemented by an HTTP adapter that
 * calls the Go catalog API in a single batch request.
 */
public interface ProductCatalogPort {

    Mono<ProductLookupResult> fetchProducts(List<String> productIds);
}
