package com.grupomariposa.orderworker.domain.product;

import java.util.List;

/**
 * Outcome of a batch product lookup: the products that exist plus the ids that
 * do not. Mirrors the catalog API's partial-success contract so the business
 * rule ("all products must exist") lives in the domain, not in the adapter.
 */
public record ProductLookupResult(List<Product> found, List<String> notFound) {

    public ProductLookupResult {
        found = List.copyOf(found);
        notFound = List.copyOf(notFound);
    }

    public boolean hasMissing() {
        return !notFound.isEmpty();
    }
}
