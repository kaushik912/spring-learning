# Spring Learning

Umbrella repo for Spring / Spring Boot learning projects.

## Projects

- [`learning-microservices/`](learning-microservices/) — microservices architecture,
  based on `reference_repos/fully-completed-microservices-Java-Springboot`.
- [`saga-orchestrator-tracing-demo/`](saga-orchestrator-tracing-demo/) — saga orchestration over Kafka
  (order/inventory/payment) with one end-to-end trace in Jaeger (OTel Java agent), incl. compensation.
- [`scheduled-email-dedup-demo/`](scheduled-email-dedup-demo/) — `@Scheduled` job on multiple instances, 1000 users,
  one email each: ShedLock + outbox unique constraint + `SKIP LOCKED` workers + provider idempotency key (Postgres).
- [`scheduled-email-simple-demo/`](scheduled-email-simple-demo/) — same problem, simple version (Spring Data JPA):
  ShedLock + one `emails_sent` status table (PENDING/SENT/FAILED, 3 attempts), sweeper retries, support `resend` endpoint.
- [`scheduled-email-simple-jdbc-demo/`](scheduled-email-simple-jdbc-demo/) — the same simple version with plain SQL
  (JdbcTemplate): fewer files, faster tests; README compares it with the JPA one.
