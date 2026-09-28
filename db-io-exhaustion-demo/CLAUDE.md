# db-io-exhaustion-demo

Demo: DB connection-pool exhaustion from index-defeating query. `buggy/BuggyOrderRepository` wraps `customer_email` in scalar fn `SLOW_EMAIL_MATCH(...)` -> full scan, index unused; slow queries hold Hikari connections, concurrent load starves pool. Nothing throws on single call. Fix in `fixed/`. Details: `bug-fix-notes-db-io-exhaustion.md`.

## Stack
Spring Boot 4.1.1, Java 17, webmvc, data-jpa, actuator, H2 (+h2console), mysql-connector-j, springdoc 3.1.1.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (only context-load test exists)

## Config
- Default DB: in-memory H2 `jdbc:h2:mem:dbio`; no Docker needed. Seeds `app.seed.row-count=200000` rows on startup (`shared/OrderDataSeeder`).
- Hikari deliberately tiny: max-pool-size=3, connection-timeout=2000ms (repro w/ little concurrency).
- Endpoints exposed: actuator health,prometheus,metrics.
- Port: default 8080.
- `mysql` Spring profile enables `mysql/MySqlBuggyOrder*` (real MySQL, external; not provided by repo). Env: SPRING_PROFILES_ACTIVE, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD (see `jmx-remote-steps.md`; also remote JMX port 7091 for JMC, needs WSL IP).

## Layout
`com.example.dbioexhaustiondemo`: `buggy`, `fixed`, `shared` (CustomerOrder, OrderDataSeeder), `mysql`.

## Gotchas
- `ddl-auto=none`; schema from schema.sql.
- open-in-view=false.
