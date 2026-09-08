---
status: approved
---
# Movie ticket booking backend — Plan

## Architecture

Single Spring Boot 3 (Java, Maven) module, layered `Controller -> Service -> Repository (Spring Data JPA)`, Postgres as the datastore. No auth, no payment, single process (no distributed locking needed).

### Entities

- **Movie**: `id`, `title`, `description`, `durationMinutes`, `genre`.
- **Showtime**: `id`, `movie_id` (FK), `startTime`, `screenLabel`.
- **Seat**: `id`, `showtime_id` (FK), `seatLabel` (e.g. `A1`), `status` (`AVAILABLE` | `BOOKED`), `booking_id` (nullable FK to Booking). One row per seat per showtime, pre-created when the showtime is created.
- **Booking**: `id`, `showtime_id` (FK), `createdAt`. Immutable once created — no cancel/refund in scope.

Seat carries its own `booking_id` (nullable) rather than a separate seat<->booking join table — a seat belongs to at most one booking, so the join table would add nothing.

### Endpoints

- `GET /movies` — list movies
- `GET /movies/{id}/showtimes` — list showtimes for a movie
- `GET /showtimes/{id}` — showtime detail, including every seat's id/label/status
- `POST /showtimes/{id}/bookings` — body `{ "seatIds": [...] }`; `201` with booking id + seats on success, `409` (all-or-nothing failure, naming the unavailable seats) if any requested seat is not `AVAILABLE`

## Key decisions

1. **Concurrency mechanism — conditional UPDATE, not `@Version`.** Booking a showtime runs in one `@Transactional` service method. For each requested seat it issues `UPDATE seat SET status='BOOKED', booking_id=:bid WHERE id=:id AND status='AVAILABLE'` (Spring Data `@Modifying @Query`) and checks the affected-row count. The `UPDATE` itself takes the DB row lock, so a second concurrent transaction touching the same seat blocks until the first commits, then sees `status='BOOKED'` and updates 0 rows. 0 rows on *any* requested seat throws, rolling back the whole transaction — true all-or-nothing with no partial state, no retry loop needed. This is the "DB-level constraint + transactions" leg of the acceptance criteria; `@Version`/optimistic-lock isn't needed since there's no other write path to Seat.
2. **`SeatStatus` has two values, not three.** Acceptance text says "available/reserved/booked", but non-goals explicitly rule out holds/expiry timers/waiting lists — there's no operation in this scope that ever produces a `RESERVED` state. Modeling a third enum value with no reachable transition would be dead code, so `SeatStatus` is `AVAILABLE | BOOKED`. Flagging this interpretation now in case it's wrong.
3. **Schema via Flyway, not `ddl-auto`.** Explicit migrations give real `NOT NULL`/`FK`/`UNIQUE(showtime_id, seat_label)` constraints, and the same migrations run against the Testcontainers Postgres in tests — schema-under-test matches schema-in-prod exactly.
4. **Demo data via a Flyway seed migration** (movies/showtimes/seats) for manual/local exploration through the API. Tests never depend on seed content — each test builds its own fixtures via repositories, so seed data can change without breaking tests.
5. **Booking has no status field.** It either exists (succeeded) or doesn't (transaction rolled back) — no partial/pending state to represent, matching non-goals.

## Testing seam

**Single seam**, per ticket. One test type: full-stack HTTP tests via `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, against a real Postgres via Testcontainers (`@ServiceConnection`, Spring Boot 3.1+ auto-config — no manual `@DynamicPropertySource` needed). No mocking of service/repository layers. Assertions land on HTTP status + response body + read-back state (e.g. `GET /showtimes/{id}` after a booking) — never on internal method calls.

Real HTTP over a random port (rather than `MockMvc`) is used specifically so the concurrency test exercises real separate threads/connections through the actual transaction boundary, not just in-process dispatch.

**Concurrency test**: two threads (`ExecutorService`, synchronized via `CountDownLatch`) `POST` a booking for the *same* seat at the same time. Assert exactly one `201` and one `409`, then `GET` the showtime and assert the seat is `BOOKED` and owned by exactly one booking (read-back, not internal state).

## API docs

`springdoc-openapi-starter-webmvc-ui` (2.8.6). Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs`. Controllers annotated with `@Tag` (class) and `@Operation` (method), per project convention.

## Risks

- **Docker required** for Testcontainers-Postgres — confirmed with user (2026-09-08), OK to use.
- Conditional-UPDATE concurrency relies on Postgres's row-level lock semantics under the default `READ COMMITTED` isolation — correct for this use case (each UPDATE is a single-row CAS), but worth calling out explicitly in code comments since it's not obvious from reading the entity alone.
