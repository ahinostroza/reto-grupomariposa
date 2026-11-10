# order-worker (Java 21 · Spring Boot · WebFlux)

Reactive Kafka worker that enriches, validates and persists orders. Hexagonal:
the domain and use case know nothing about Kafka, HTTP, Mongo or Redis.

## Structure

```
src/main/java/com/grupomariposa/orderworker/
├── domain/              # pure domain: Order, Customer, Product, Money, rules, exceptions
├── application/         # ProcessOrderService + ports (in: use case; out: catalog,
│                        #   customer, repository, lock, failed-store)
└── infrastructure/
    ├── adapter/inbound/kafka/    # reactor-kafka consumer + DLT
    └── adapter/outbound/
    │   ├── catalog/ customer/    # WebClient + Resilience4j + Caffeine
    │   ├── mongo/                # reactive Mongo, upsert by orderId
    │   └── redis/                # Redisson lock + failed-message store
    └── config/          # composition root (beans wire the framework-free core)
```

## Build & test

```bash
./mvnw verify        # unit tests (Surefire) + integration tests (Failsafe, Testcontainers)
./mvnw test          # unit tests only
```

Integration tests need Docker (Kafka/Mongo/Redis containers); they are skipped
automatically when Docker is unavailable.

## Configuration (env vars)

`KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `MONGODB_URI`, `REDIS_URL`,
`CATALOG_BASE_URL`, `CUSTOMER_BASE_URL`. Defaults target `localhost`; see
`application.yml`.
