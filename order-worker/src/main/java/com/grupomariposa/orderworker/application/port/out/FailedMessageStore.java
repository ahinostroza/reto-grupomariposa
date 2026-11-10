package com.grupomariposa.orderworker.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port for recording messages that could not be processed. Backed by
 * Redis: it keeps an attempt counter per order plus the last failure reason, so
 * the consumer can enforce a maximum-retries policy and operators can inspect
 * what failed. Implemented over Redis.
 */
public interface FailedMessageStore {

    /**
     * Records a failure for the given order: stores the original message payload
     * and the reason, increments the attempt counter, and returns the new count.
     */
    Mono<Long> recordFailure(String orderId, String payload, String reason);
}
