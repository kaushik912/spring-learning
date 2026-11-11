# saga-orchestrator-tracing-demo

Demo: saga orchestration (order-service) over Kafka with inventory/payment services; distributed trace in Jaeger via OTel Java agent. Full docs in README.md.

## Stack
Java 21, Boot 3.5.16, spring-kafka (JSON, no type headers), JdbcTemplate + Flyway, Postgres, Kafka KRaft, OTel agent 2.33.1.

## Commands
- Run all: `docker compose up --build` (Docker; ask user first). Jaeger :16686, order-service :8080.
- Test: `mvn test` (integration-tests module, Testcontainers, needs Docker). No mvnw in repo.

## Layout
Maven multi-module: `common` (Command/Reply records, `SagaTracing`, shared logback-spring.xml), `order-service` (SagaOrchestrator = state machine), `inventory-service`, `payment-service`, `integration-tests`.

## Gotchas
- Config files are `<service>.yml` not application.yml; each `*Application.start()` sets `spring.config.name`. Needed so integration-tests can run 3 services in one JVM. Flyway locations are per service (`db/migration/<svc>`) for same reason.
- Service jars use classifier `exec`; plain jar is the main artifact (IT module depends on it). Dockerfile copies `*-exec.jar`.
- OTel API in app is no-op without agent. Timeout/retry spans restore parent from `saga_instance.trace_parent`; poll runs under `SagaTracing.unsampled()` to avoid noise traces.
- INFO logs only for saga milestones; rest DEBUG.
- Failure injection (payment): amount>1000 fail, ==777 sleeps 10s.
