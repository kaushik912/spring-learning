# idempotency-key-race-demo

Demo: check-then-act idempotency key -> double charge under concurrent retries. `buggy/InMemoryIdempotencyStore` has separate `hasBeenSeen` + `markSeen` (race). Fixes: `fixed/redis` (atomic `setIfAbsent`/SETNX), `fixed/db` (unique constraint on IdempotencyRecord). `charge/ChargeSimulator` fakes gateway. Details: `bug-fix-notes-idempotency.md`.

## Stack
Spring Boot 4.1.1, Java 17, webmvc, data-jpa, data-redis, H2 (+h2console), springdoc 3.1.1.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (RaceConditionReproductionTest, @SpringBootTest RANDOM_PORT)

## Deps
- H2 in-mem `jdbc:h2:mem:idempotency`, ddl-auto=update.
- Redis at localhost:6379 (spring.data.redis.host/port); repo has no docker-compose and no embedded/mocked Redis in tests found -> Redis endpoint/test likely needs local Redis running. Ask before Docker.
- Port: default 8080. No env vars.

## Endpoints
`/api/fixed/redis/payments/{key}` (redis fix); buggy and `fixed/db` controllers in respective pkgs.

## Layout
`com.example.idempotencykeyracedemo`: `buggy`, `charge`, `fixed.redis`, `fixed.db`.

## Gotchas
- DB-fix intentionally triggers unique-violations; `logging.level.org.hibernate.orm.jdbc.error=off` silences them.
