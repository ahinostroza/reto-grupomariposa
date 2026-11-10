package com.grupomariposa.orderworker.infrastructure.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderConsumerIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    @Container
    static final MongoDBContainer mongo =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    // In-process stub for the Go enrichment APIs. Only "product-789" and
    // "customer-456" are known; anything else is reported as not found.
    static final MockWebServer goApis = new MockWebServer();

    static {
        goApis.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/v1/customers/")) {
                    return json("{\"customerId\":\"customer-456\",\"name\":\"Ada\",\"status\":\"ACTIVE\"}");
                }
                if (path != null && path.equals("/v1/products:batchGet")) {
                    return batchResponse(request);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        try {
            goApis.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.topic", () -> "orders");
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("redisson.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        String base = goApis.url("/").toString();
        registry.add("enrichment.catalog-base-url", () -> base);
        registry.add("enrichment.customer-base-url", () -> base);
    }

    @AfterAll
    static void stopStub() throws IOException {
        goApis.shutdown();
    }

    @Autowired
    private ReactiveMongoTemplate template;

    @Test
    void consumesEnrichesValidatesAndPersistsAnOrder() {
        produce("order-1",
                "{\"orderId\":\"order-1\",\"customerId\":\"customer-456\",\"productIds\":[\"product-789\"]}");

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            Long count = template.count(byOrderId("order-1"), "processed_orders").block();
            assertEquals(1L, count);
        });
    }

    @Test
    void deadLettersAnOrderWithAnUnknownProductAndDoesNotPersistIt() {
        produce("order-2",
                "{\"orderId\":\"order-2\",\"customerId\":\"customer-456\",\"productIds\":[\"ghost\"]}");

        RedissonClient probe = redissonProbe();
        try {
            await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
                long attempts = probe.getAtomicLong("failed:order:order-2:attempts").get();
                assertEquals(1L, attempts, "the failed order must be recorded once");
            });
        } finally {
            probe.shutdown();
        }

        Long persisted = template.count(byOrderId("order-2"), "processed_orders").block();
        assertEquals(0L, persisted, "an invalid order must not be persisted");
    }

    private static Query byOrderId(String orderId) {
        return Query.query(Criteria.where("orderId").is(orderId));
    }

    private static void produce(String key, String value) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>("orders", key, value));
            producer.flush();
        }
    }

    private static RedissonClient redissonProbe() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        return Redisson.create(config);
    }

    private static MockResponse batchResponse(RecordedRequest request) {
        try {
            JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
            List<String> notFound = new ArrayList<>();
            boolean hasKnown = false;
            for (JsonNode id : body.get("ids")) {
                if ("product-789".equals(id.asText())) {
                    hasKnown = true;
                } else {
                    notFound.add("\"" + id.asText() + "\"");
                }
            }
            String products = hasKnown
                    ? "{\"productId\":\"product-789\",\"name\":\"Laptop\",\"price\":99900,\"currency\":\"USD\",\"active\":true}"
                    : "";
            return json("{\"products\":[" + products + "],\"notFound\":[" + String.join(",", notFound) + "]}");
        } catch (Exception e) {
            return new MockResponse().setResponseCode(500);
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
