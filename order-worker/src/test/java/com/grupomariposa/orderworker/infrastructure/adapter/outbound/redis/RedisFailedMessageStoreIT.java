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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class RedisFailedMessageStoreIT {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    private RedissonClient client;
    private RedisFailedMessageStore store;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        client = Redisson.create(config);
        store = new RedisFailedMessageStore(client.reactive(), Duration.ofMinutes(10));
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    private static final String PAYLOAD = "{\"orderId\":\"order-1\",\"customerId\":\"c-1\",\"productIds\":[\"p-1\"]}";

    @Test
    void incrementsAttemptCounterPerOrder() {
        Long first = store.recordFailure("order-1", PAYLOAD, "timeout").block();
        Long second = store.recordFailure("order-1", PAYLOAD, "5xx from catalog").block();

        assertEquals(1L, first);
        assertEquals(2L, second);
    }

    @Test
    void storesThePayloadAlongsideTheReason() {
        store.recordFailure("order-1", PAYLOAD, "timeout").block();

        String storedPayload = client.reactive()
                .<String>getBucket("failed:order:order-1:payload").get().block();
        String storedReason = client.reactive()
                .<String>getBucket("failed:order:order-1:reason").get().block();

        assertEquals(PAYLOAD, storedPayload);
        assertEquals("timeout", storedReason);
    }

    @Test
    void countersAreIsolatedPerOrder() {
        store.recordFailure("order-A", PAYLOAD, "boom").block();
        Long orderB = store.recordFailure("order-B", PAYLOAD, "boom").block();

        assertEquals(1L, orderB, "each order must keep its own counter");
    }
}
