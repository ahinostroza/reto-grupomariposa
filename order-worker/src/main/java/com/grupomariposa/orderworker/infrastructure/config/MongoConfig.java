package com.grupomariposa.orderworker.infrastructure.config;

import com.grupomariposa.orderworker.application.port.out.ProcessedOrderRepository;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.MongoProcessedOrderRepository;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.mongo.document.ProcessedOrderDocument;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Wiring for the MongoDB persistence adapter. The unique index on {@code orderId}
 * is created explicitly at startup instead of relying on auto-index-creation
 * (which is discouraged in production, where indexes are managed deliberately).
 */
@Configuration
public class MongoConfig {

    @Bean
    ProcessedOrderRepository processedOrderRepository(ReactiveMongoTemplate template) {
        return new MongoProcessedOrderRepository(template);
    }

    @Bean
    ApplicationRunner processedOrderIndexInitializer(ReactiveMongoTemplate template) {
        return args -> template.indexOps(ProcessedOrderDocument.class)
                .ensureIndex(new Index().on("orderId", Sort.Direction.ASC).unique())
                .block();
    }
}
