# payment-volume-streaming-demo

Demo (not a bug): real-time total money moved in past 24h via Kafka Streams. Payments (USD/EUR/GBP) -> topic `payment-events` -> converted to USD using FX rate at payment's own timestamp -> hopping-window aggregate in WindowStore `payment-volume-store` -> read via Interactive Queries. See README.md.

## Stack
Spring Boot 4.1.1, Java 17, spring-boot-starter-kafka, kafka-streams, webmvc, validation, springdoc; test: kafka-streams-test-utils, @EmbeddedKafka.

## Commands
- Run: `./mvnw spring-boot:run`; short windows: `-Dspring-boot.run.profiles=demo` (2m window/10s advance vs default 24h/1h)
- Test: `./mvnw test` (topology tests via TopologyTestDriver; API IT uses @EmbeddedKafka)

## Kafka
- Manual runs need Kafka localhost:9092: `docker compose up -d` (apache/kafka 3.9.1). Ask user before Docker.
- Config in application.yml: `payments.window.*`, `payments.fx.*` (mock FX jitter), `payments.simulator.*` (enabled by default, 0.5s interval). Port default 8080. No env vars.

## Endpoints
POST `/api/payments` (202), GET `/api/payments/volume`; Swagger `/swagger-ui.html`.

## Layout
`com.example.paymentvolumestreamingdemo`: `web` (+dto), `streams` (PaymentVolumeTopology), `kafka`, `conversion` (Mock FX), `simulator`, `config`, `model`.

## Gotchas
- Hopping (not sliding) windows: total stale up to 1 advance interval.
- State: RocksDB local + Kafka changelog topic.
- Simulator on by default -> constant traffic.
