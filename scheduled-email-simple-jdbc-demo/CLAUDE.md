# scheduled-email-simple-jdbc-demo

Simple version of the scheduled-email problem, plain SQL via JdbcTemplate: ShedLock-guarded `send` + `sweep` jobs over one `emails_sent` table (PENDING -> SENT | FAILED, attempts, max 3), provider idempotency key `runId:userId:rN`, support `resend` endpoint. Same design/behaviour as `../scheduled-email-simple-demo` (JPA) and a light alternative to `../scheduled-email-dedup-demo` (heavy). Details/limits in `README.md`.

## Stack
Spring Boot 4.1.1, Java 21, webmvc, jdbc (JdbcTemplate), Postgres 17 (docker compose, host port 5435), ShedLock 6.6.0, Testcontainers 2.x.

## Commands
- Test: `./mvnw test` (Testcontainers Postgres; 3 tests)
- Multi-JVM run: `./run-instances.sh` (ports 8093/8094, logs in `logs/`)
- Cleanup: `docker compose down -v`
- Endpoints: `GET /admin/emails/report`, `POST /admin/emails/{runId}/{userId}/resend?by=`

## Gotchas
- `src/test/resources/application.properties` shadows main entirely; keep `demo.*` complete there.
- Test container is started statically (not @Container) so it outlives the cached Spring context.
- Tests call `EmailService` directly (not the `@SchedulerLock` wrappers) to simulate overlapping runners.
- Status changes are conditional UPDATEs (`... AND status <> 'SENT'`), never load-modify-save.
- `runId` = `LocalDate.now()`; `sweep` retries FAILED rows of any run, `send` picks up PENDING of any run.
- `run-instances.sh` must wait for rows to exist before declaring done (an empty report also looks "finished").
