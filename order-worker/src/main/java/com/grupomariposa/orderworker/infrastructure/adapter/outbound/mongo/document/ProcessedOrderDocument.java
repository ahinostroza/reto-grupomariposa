package com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * MongoDB representation of a processed order. The unique index on {@code orderId}
 * is created explicitly at startup (see MongoConfig); the annotation documents intent.
 */
@Document(collection = "processed_orders")
public record ProcessedOrderDocument(
        @Id String id,
        @Indexed(unique = true) String orderId,
        String customerId,
        List<ProductDocument> products) {
}
