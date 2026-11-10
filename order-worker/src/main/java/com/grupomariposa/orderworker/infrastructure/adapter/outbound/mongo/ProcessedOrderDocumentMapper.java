package com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo;

import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document.ProcessedOrderDocument;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document.ProductDocument;

import java.util.List;

/**
 * Maps the domain {@link EnrichedOrder} to its persisted projection. Pure and
 * static so the projection logic is unit-testable without a database.
 */
final class ProcessedOrderDocumentMapper {

    private ProcessedOrderDocumentMapper() {
    }

    static ProcessedOrderDocument toDocument(EnrichedOrder order) {
        List<ProductDocument> products = order.products().stream()
                .map(p -> new ProductDocument(p.productId(), p.name(), p.price().amountMinor()))
                .toList();
        // id is null: MongoDB assigns the ObjectId on insert.
        return new ProcessedOrderDocument(null, order.orderId(), order.customerId(), products);
    }
}
