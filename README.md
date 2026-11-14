# Spring Learning

Umbrella repo for Spring / Spring Boot learning projects.

## Projects

- [`learning-microservices/`](learning-microservices/) — microservices architecture,
  based on `reference_repos/fully-completed-microservices-Java-Springboot`.
- [`saga-orchestrator-tracing-demo/`](saga-orchestrator-tracing-demo/) — saga orchestration over Kafka
  (order/inventory/payment) with one end-to-end trace in Jaeger (OTel Java agent), incl. compensation.
- [`scheduled-email/`](scheduled-email/) — one `@Scheduled` job on multiple instances must email 1000 users exactly
  once (ShedLock, unique keys, retries, idempotency). Three demos of the same problem: heavy outbox/workers design,
  and a simple status-table design in JPA and in plain SQL. Start with its README.
