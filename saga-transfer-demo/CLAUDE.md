# saga-transfer-demo

Demo: no distributed transaction across two services' DBs. `buggy/BuggyTransferController` debits sender then credits receiver as if atomic; crash between -> money lost. Sender/receiver = separate in-memory stores (`accounts/sender/SenderAccountStore`, `accounts/receiver/ReceiverAccountStore`). Fix: Kafka-based orchestrated saga in `saga/` (SagaOrchestrator, Sender/ReceiverSagaListener, commands, SagaState/SagaStatus). Details: `bug-fix-notes-saga.md`.

## Stack
Spring Boot 4.1.1, Java 17, spring-boot-starter-kafka, webmvc, springdoc 3.1.1.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (TransferSagaReproductionTest; embedded Kafka via `${spring.embedded.kafka.brokers}` in test props; no Docker)

## Kafka
- Manual runs need Kafka localhost:9092: `docker compose up -d` (apache/kafka 3.9.1). Ask user before Docker.
- JSON (de)serializers; trusted package `com.example.sagatransferdemo.saga`. auto-offset-reset=earliest.
- Port default 8080. No env vars.

## Layout
`com.example.sagatransferdemo`: `buggy`, `saga`, `accounts` (AccountQueryController + stores).

## Gotchas
- Stores in-memory, single process; state lost on restart.
- New saga message classes must live under trusted package or deserialization fails.
