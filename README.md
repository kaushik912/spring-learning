# Spring Learning

Umbrella repo for Spring / Spring Boot learning projects.

## Projects

- [`learning-microservices/`](learning-microservices/) — microservices architecture,
  based on `reference_repos/fully-completed-microservices-Java-Springboot`.
- [`saga-orchestrator-tracing-demo/`](saga-orchestrator-tracing-demo/) — saga orchestration over Kafka
  (order/inventory/payment) with one end-to-end trace in Jaeger (OTel Java agent), incl. compensation.
- [`scheduled-email/`](scheduled-email/) — one `@Scheduled` job on multiple instances must email 1000 users exactly
  once (ShedLock, unique keys, retries, idempotency). Four demos of the same problem: a simple status-table design
  (plain SQL and JPA), a heavy hand-rolled outbox/workers design, and a Kafka design (consumer group + dead-letter
  topic). Start with its README.
