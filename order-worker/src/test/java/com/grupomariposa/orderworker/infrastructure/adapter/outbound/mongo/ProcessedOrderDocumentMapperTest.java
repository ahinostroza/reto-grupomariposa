package com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo;

import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import com.grupomariposa.orderworker.domain.product.Money;
import com.grupomariposa.orderworker.domain.product.Product;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document.ProcessedOrderDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessedOrderDocumentMapperTest {

    @Test
    void mapsToPersistedProjectionWithPriceInMinorUnits() {
        EnrichedOrder order = new EnrichedOrder("order-1", "customer-456",
                List.of(new Product("product-789", "Laptop", new Money(99900, "USD"))));

        ProcessedOrderDocument doc = ProcessedOrderDocumentMapper.toDocument(order);

        assertNull(doc.id(), "id must be assigned by MongoDB on insert");
        assertEquals("order-1", doc.orderId());
        assertEquals("customer-456", doc.customerId());
        assertEquals(1, doc.products().size());
        assertEquals("product-789", doc.products().get(0).productId());
        assertEquals(99900, doc.products().get(0).price());
    }
}
