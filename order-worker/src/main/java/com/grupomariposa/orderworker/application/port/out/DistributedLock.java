package com.grupomariposa.orderworker.application.port.out;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Outbound port for distributed locking. The action is a deferred supplier so it
 * runs only after the lock is acquired, and the lock is always released when the
 * returned Mono terminates. Implemented with Redisson over Redis.
 */
public interface DistributedLock {

    <T> Mono<T> executeExclusively(String key, Supplier<Mono<T>> action);
}
