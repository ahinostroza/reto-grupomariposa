package com.grupomariposa.orderworker.infrastructure.adapter.outbound.redis;

import com.grupomariposa.orderworker.application.port.out.DistributedLock;
import com.grupomariposa.orderworker.domain.exception.LockNotAcquiredException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis-backed distributed lock using Redisson.
 *
 * <p>The lock is taken with a lease time, so if this instance dies mid-processing
 * the lock expires instead of deadlocking. A random owner id is used for both
 * lock and unlock to stay consistent across reactor threads (the async API binds
 * ownership to an explicit id rather than the calling thread). {@code usingWhen}
 * guarantees the lock is released on success, error and cancellation.
 */
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redisson;
    private final Duration waitTime;
    private final Duration leaseTime;

    public RedissonDistributedLock(RedissonClient redisson, Duration waitTime, Duration leaseTime) {
        this.redisson = redisson;
        this.waitTime = waitTime;
        this.leaseTime = leaseTime;
    }

    @Override
    public <T> Mono<T> executeExclusively(String key, Supplier<Mono<T>> action) {
        return Mono.defer(() -> {
            RLock lock = redisson.getLock(key);
            long ownerId = ThreadLocalRandom.current().nextLong();

            return Mono.fromCompletionStage(lock.tryLockAsync(
                            waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS, ownerId))
                    .flatMap(acquired -> {
                        if (!Boolean.TRUE.equals(acquired)) {
                            return Mono.error(new LockNotAcquiredException(key));
                        }
                        return Mono.usingWhen(
                                Mono.just(lock),
                                held -> action.get(),
                                held -> Mono.fromCompletionStage(held.unlockAsync(ownerId)));
                    });
        });
    }
}
