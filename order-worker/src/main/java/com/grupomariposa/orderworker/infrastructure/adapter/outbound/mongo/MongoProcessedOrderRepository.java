package com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo;

import com.grupomariposa.orderworker.application.port.out.ProcessedOrderRepository;
import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document.ProcessedOrderDocument;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;

/**
 * MongoDB implementation of {@link ProcessedOrderRepository}.
 *
 * <p>{@link #save} is an <b>upsert keyed by orderId</b>: a re-delivered order
 * updates the existing document instead of inserting a duplicate. Combined with
 * the unique index on {@code orderId}, this makes persistence idempotent even
 * under concurrent delivery.
 */
public class MongoProcessedOrderRepository implements ProcessedOrderRepository {

    private final ReactiveMongoTemplate template;

    public MongoProcessedOrderRepository(ReactiveMongoTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<Boolean> existsByOrderId(String orderId) {
        return template.exists(byOrderId(orderId), ProcessedOrderDocument.class);
    }

    @Override
    public Mono<Void> save(EnrichedOrder order) {
        ProcessedOrderDocument doc = ProcessedOrderDocumentMapper.toDocument(order);
        Update update = new Update()
                .set("customerId", doc.customerId())
                .set("products", doc.products());
        return template.upsert(byOrderId(order.orderId()), update, ProcessedOrderDocument.class).then();
    }

    private static Query byOrderId(String orderId) {
        return Query.query(Criteria.where("orderId").is(orderId));
    }
}
