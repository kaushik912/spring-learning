# scheduled-email-kafka-demo

Kafka version of the scheduled-email problem: ShedLock-guarded publisher produces one message per user to `email-requests` (key = userId, 6 partitions); consumer group `email-senders` dedups (`email_results`), sends with provider idempotency key `runId:userId:rN`, retries via `DefaultErrorHandler`, dead-letters to `email-requests-dlt` (recorded as DEAD); support resend = new message with resendCount+1. Details in `README.md`. Siblings: `../scheduled-email-simple-jdbc-demo`, `../scheduled-email-dedup-demo`.

## Stack
Spring Boot 4.1.1, Java 21, webmvc, spring-boot-starter-kafka (spring-kafka 4.x), jdbc (JdbcTemplate), Postgres 17 (host 5436), Kafka apache/kafka 3.9.1 KRaft (host 9094), ShedLock 6.6.0, Testcontainers 2.x.

## Commands
- Test: `./mvnw test` (Testcontainers Kafka + Postgres; 4 tests, ~90s)
- Multi-JVM run: `./run-instances.sh` (ports 8095/8096, logs in `logs/`, kill -9 of 8096 unless `NO_KILL=1`)
- Cleanup: `docker compose down -v`
- Endpoints: `GET /admin/emails/report`, `POST /admin/emails/{runId}/publish`, `POST /admin/emails/{runId}/{userId}/resend`

## Gotchas
- Needs Docker running with WSL integration; if `docker` is "not found in this WSL 2 distro", start Docker Desktop / enable integration first.
- `src/test/resources/application.properties` shadows main entirely; keep `spring.kafka.consumer.*`, `spring.kafka.listener.concurrency` and `demo.*` complete there (bootstrap servers + datasource come from @ServiceConnection).
- Test containers are started statically so they outlive the cached Spring context (its consumers keep running).
- Offset commit = listener returned normally (default ack mode); no manual ack code.
- `alreadySent` counts a SENT row with resend_count >= request's, so stale r0 after a sent r1 is skipped.
- Re-publishing a run retries dead-lettered users (they are not SENT); that is intended.
- DLT must have >= partitions of the source topic (recoverer keeps the partition number).
