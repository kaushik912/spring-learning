# scheduled-email-simple-demo

Simple version of `../scheduled-email-dedup-demo`: ShedLock-guarded `send` + `sweep` jobs over one `emails_sent` table (PENDING -> SENT | FAILED, attempts, max 3), provider idempotency key `runId:userId:rN`, support `resend` endpoint. Details/limits in `README.md`.

## Stack
Spring Boot 4.1.1, Java 21, webmvc, Spring Data JPA (Hibernate, `ddl-auto=validate`; tables from `schema.sql`), Postgres 17 (docker compose, host port 5434), ShedLock 6.6.0, Testcontainers 2.x.

## Commands
- Test: `./mvnw test` (Testcontainers Postgres; 3 tests)
- Multi-JVM run: `./run-instances.sh` (ports 8091/8092, logs in `logs/`)
- Cleanup: `docker compose down -v`
- Endpoints: `GET /admin/emails/report`, `POST /admin/emails/{runId}/{userId}/resend?by=`

## Gotchas
- `src/test/resources/application.properties` shadows main entirely; keep `demo.*` complete there.
- Test container is started statically (not @Container) so it outlives the cached Spring context.
- Tests call `EmailService` directly (not the `@SchedulerLock` wrappers) to simulate overlapping runners.
- `EmailRecord` implements `Persistable` (assigned id): without it `save()` merges and could reset SENT -> PENDING in a race. Status changes use conditional `@Modifying @Query` updates, not load-modify-save.
- Entity columns must match `schema.sql` (`validate`); schema/entity edits go together.
- `runId` = `LocalDate.now()`; `sweep` retries FAILED rows of any run, `send` picks up PENDING of any run.
