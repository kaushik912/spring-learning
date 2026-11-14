# scheduled-email-dedup-demo

Demo: N app instances, `@Scheduled` job must email each of 1000 users once. Layers: ShedLock publisher -> outbox `UNIQUE(run_id,user_id)` -> `SKIP LOCKED` claim workers -> mock provider idempotency key -> reaper. Choices/tradeoffs in `README.md`.

## Stack
Spring Boot 4.1.1, Java 21, webmvc, jdbc (JdbcTemplate, no JPA), Postgres 17 (docker compose, host port 5433), ShedLock 6.6.0 (jdbc-template), Testcontainers 2.x.

## Commands
- Test: `./mvnw test` (Testcontainers Postgres, 1000 users, chaos on)
- Multi-JVM run: `./run-instances.sh 3` (compose Postgres, ports 8081.., logs in `logs/`, kill -9 of 8082 unless `NO_KILL=1`)
- Cleanup: `docker compose down -v`
- Endpoints: `POST /api/publish?runId=`, `GET /api/report?runId=`

## Gotchas
- `src/test/resources/application.properties` shadows main entirely; keep `demo.*` complete there (datasource comes from @ServiceConnection).
- Test container is started statically (not @Container) so it outlives the cached Spring context; per-class stop causes teardown errors.
- Start first instance and wait until up before others (schema.sql concurrent CREATE TABLE race); script polls `/api/report`.
- Never use bare `wait` in scripts here (waits on JVMs); never `set -e` + fixed sleeps before curl.
- Schema is `CREATE TABLE IF NOT EXISTS`; stale data from old runs persists in the compose volume (use unique runIds or `down -v`).
