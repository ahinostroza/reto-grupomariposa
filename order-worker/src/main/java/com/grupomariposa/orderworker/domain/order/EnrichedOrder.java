package com.grupomariposa.orderworker.domain.order;

import com.grupomariposa.orderworker.domain.product.Product;

import java.util.List;

/**
 * An order after enrichment and validation, ready to be persisted.
 */
public record EnrichedOrder(String orderId, String customerId, List<Product> products) {

    public EnrichedOrder {
        products = List.copyOf(products);
    }
}
