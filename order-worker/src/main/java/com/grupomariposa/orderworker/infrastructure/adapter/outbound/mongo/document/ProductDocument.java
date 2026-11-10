package com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document;

/**
 * Persisted product projection. Only the fields the challenge stores are kept;
 * price is the amount in minor units (an integer), never a floating-point value.
 */
public record ProductDocument(String productId, String name, long price) {
}
