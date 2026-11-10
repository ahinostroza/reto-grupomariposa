package com.grupomariposa.orderworker.infrastructure.adapter.outbound.redis;

import com.grupomariposa.orderworker.application.port.out.FailedMessageStore;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RedissonReactiveClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed store for failed messages. Keeps, per order, an attempt counter
 * and the last failure reason, both with a TTL so entries self-expire. The
 * counter lets the consumer enforce a maximum-retries policy.
 */
public class RedisFailedMessageStore implements FailedMessageStore {

    private static final String PREFIX = "failed:order:";

    private final RedissonReactiveClient redisson;
    private final Duration ttl;

    public RedisFailedMessageStore(RedissonReactiveClient redisson, Duration ttl) {
        this.redisson = redisson;
        this.ttl = ttl;
    }

    @Override
    public Mono<Long> recordFailure(String orderId, String payload, String reason) {
        String base = PREFIX + orderId;
        RAtomicLongReactive attempts = redisson.getAtomicLong(base + ":attempts");
        RBucketReactive<String> payloadBucket = redisson.getBucket(base + ":payload");
        RBucketReactive<String> reasonBucket = redisson.getBucket(base + ":reason");

        return attempts.incrementAndGet()
                .flatMap(count -> attempts.expire(ttl)
                        .then(payloadBucket.set(payload, ttl))
                        .then(reasonBucket.set(reason, ttl))
                        .thenReturn(count));
    }
}
