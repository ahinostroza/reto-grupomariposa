package com.grupomariposa.orderworker.application.port.in;

import com.grupomariposa.orderworker.domain.order.OrderCommand;
import reactor.core.publisher.Mono;

/**
 * Inbound port: the single entry point for processing an order. Inbound adapters
 * (e.g. the Kafka consumer) depend on this, never on the concrete service.
 */
public interface ProcessOrderUseCase {

    Mono<ProcessingResult> process(OrderCommand command);
}
