package com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo;

import com.grupomariposa.orderworker.application.port.out.ProcessedOrderRepository;
import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import com.grupomariposa.orderworker.domain.product.Money;
import com.grupomariposa.orderworker.domain.product.Product;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document.ProcessedOrderDocument;
import com.grupomariposa.orderworker.infrastructure.config.MongoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataMongoTest
@Import(MongoConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class MongoProcessedOrderRepositoryIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private ProcessedOrderRepository repository;

    @Autowired
    private ReactiveMongoTemplate template;

    @BeforeEach
    void cleanCollection() {
        template.dropCollection(ProcessedOrderDocument.class).block();
        template.indexOps(ProcessedOrderDocument.class)
                .ensureIndex(new Index().on("orderId", Sort.Direction.ASC).unique())
                .block();
    }

    private static EnrichedOrder sampleOrder() {
        return new EnrichedOrder("order-1", "customer-456",
                List.of(new Product("product-789", "Laptop", new Money(99900, "USD"))));
    }

    @Test
    void savesWithTheProjectedShape() {
        StepVerifier.create(repository.save(sampleOrder())).verifyComplete();

        ProcessedOrderDocument doc = template.findOne(
                byOrderId(), ProcessedOrderDocument.class).block();

        assertNotNull(doc);
        assertEquals("customer-456", doc.customerId());
        assertEquals(1, doc.products().size());
        assertEquals(99900, doc.products().get(0).price());
    }

    @Test
    void saveIsIdempotentByOrderId() {
        repository.save(sampleOrder()).block();
        repository.save(sampleOrder()).block(); // simulated Kafka re-delivery

        Long count = template.count(byOrderId(), ProcessedOrderDocument.class).block();
        assertEquals(1L, count, "a re-delivered order must not be duplicated");
    }

    @Test
    void existsReflectsPersistedState() {
        assertEquals(Boolean.FALSE, repository.existsByOrderId("order-1").block());
        repository.save(sampleOrder()).block();
        assertEquals(Boolean.TRUE, repository.existsByOrderId("order-1").block());
    }

    private static Query byOrderId() {
        return Query.query(Criteria.where("orderId").is("order-1"));
    }
}
