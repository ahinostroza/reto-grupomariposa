package com.grupomariposa.orderworker.infrastructure.adapter.outbound.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class RedissonDistributedLockIT {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    private RedissonClient client;
    private RedissonDistributedLock lock;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        client = Redisson.create(config);
        lock = new RedissonDistributedLock(client, Duration.ofSeconds(3), Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    @Test
    void runsActionUnderLockAndThenReleasesIt() {
        String first = lock.executeExclusively("order-lock:order-1", () -> Mono.just("done")).block();
        assertEquals("done", first);

        // If the lock was released, a second acquisition on the same key succeeds.
        String second = lock.executeExclusively("order-lock:order-1", () -> Mono.just("again")).block();
        assertEquals("again", second);
    }

    @Test
    void releasesLockEvenWhenTheActionFails() {
        StepVerifier.create(lock.executeExclusively("order-lock:order-2",
                        () -> Mono.error(new RuntimeException("boom"))))
                .expectErrorMessage("boom")
                .verify();

        // The lock must be free after the failure.
        String after = lock.executeExclusively("order-lock:order-2", () -> Mono.just("ok")).block();
        assertEquals("ok", after);
    }
}
