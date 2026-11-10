package com.grupomariposa.orderworker.domain.product;

/**
 * A catalog product after enrichment.
 */
public record Product(String productId, String name, Money price) {
}
