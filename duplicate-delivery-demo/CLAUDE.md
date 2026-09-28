# duplicate-delivery-demo

Demo: Kafka is at-least-once; redelivery makes consumer repeat side effect (email). `buggy/BuggyEventListener` does side effect then throws -> redelivered -> processed twice. Fixes: `fixed/FixedEventListener` (idempotent, in-memory), `fixed/persistent/*` (processed_event table via JPA; survives restart). Details: `bug-fix-notes-duplicate-delivery.md`.

## Stack
Spring Boot 4.1.1, Java 17, spring-boot-starter-kafka, webmvc, data-jpa, H2, springdoc 3.1.1.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (@EmbeddedKafka, in-memory H2; no Docker)

## Kafka / DB
- Manual runs need Kafka at localhost:9092: `docker compose up -d` (apache/kafka 3.9.1). Ask user before Docker.
- Main DB: file-based H2 `./data/duplicatedelivery` (so restart-survival is real); ddl-auto=update. Tests use `jdbc:h2:mem:duplicatedelivery-test`.
- Port: default 8080. No env vars.

## Layout
`com.example.duplicatedeliverydemo`: EventController, EventProducer, EmailService, KafkaRetryConfig, KafkaTopicsConfig, EventTopics; `buggy`, `fixed`, `fixed.persistent`.

## Gotchas
- `./data/` file DB created in cwd on run; stale rows can affect manual repro.
- Test props in src/test/resources override main.
