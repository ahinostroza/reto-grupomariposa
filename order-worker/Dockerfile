# Multi-stage build: package the Spring Boot jar, run it on a slim JRE.
# Build context is the repository root (see docker-compose.yml) so the OpenAPI
# contracts owned by the Go services are available for client DTO code generation.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY order-worker/pom.xml ./pom.xml
# Warm the dependency cache before copying sources for better layer caching.
RUN mvn -q -B dependency:go-offline
COPY order-worker/src ./src
# Contracts (owned by the Go services) for contract-first DTO generation.
COPY product-catalog/api/openapi.yaml /product-catalog/api/openapi.yaml
COPY customer/api/openapi.yaml /customer/api/openapi.yaml
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/order-worker-0.1.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
