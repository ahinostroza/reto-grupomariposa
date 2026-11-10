package com.grupomariposa.orderworker.application.port.out;

import com.grupomariposa.orderworker.domain.order.EnrichedOrder;
import reactor.core.publisher.Mono;

/**
 * Outbound port for persisting processed orders. {@link #existsByOrderId} backs
 * idempotency: Kafka delivers at-least-once, so the same order may arrive twice.
 */
public interface ProcessedOrderRepository {

    Mono<Boolean> existsByOrderId(String orderId);

    Mono<Void> save(EnrichedOrder order);
}
