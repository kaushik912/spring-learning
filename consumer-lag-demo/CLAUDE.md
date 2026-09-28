# consumer-lag-demo

Demo: Kafka consumer lag grows though consumer "healthy". `buggy/BuggyOrderListener` (1 thread, 30ms sleep ~33 msg/s) < `OrderProducer` (~66 msg/s). Lag = produce rate - consume rate; no error thrown.
Fixes: `fixed/fast/FastOrderListener`, `fixed/scaled/ScaledOrderListener`. Details: `bug-fix-notes-consumer-lag.md`.

## Stack
Spring Boot 4.1.1, Java 17, spring-boot-starter-kafka, webmvc, springdoc-openapi 3.1.1 (Swagger UI /swagger-ui.html).

## Commands
- Run: `./mvnw spring-boot:run` (mvnw present)
- Test: `./mvnw test` (ConsumerLagReproductionTest uses @EmbeddedKafka; no Docker)

## Kafka
- Manual runs need real Kafka at localhost:9092: `docker compose up -d` (apache/kafka 3.9.1, KRaft, single node). Tests do not need it. Ask user before Docker.
- Port: default 8080 (no server.port set). No env vars.

## Layout
`com.example.consumerlagdemo`: ConsumerLagService, LagController, LoadController, OrderProducer, OrderTopics, KafkaTopicsConfig; subpkgs `buggy`, `fixed.fast`, `fixed.scaled`.

## Gotchas
- ack-mode=record, auto-offset-reset=earliest.
- Test props in src/test/resources/application.properties override main.
