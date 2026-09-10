# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- Build/test: `./mvnw test` (or `make test`)
- Single test class: `./mvnw test -Dtest=BookingConcurrencyControllerTest`
- Single test method: `./mvnw test -Dtest=BookingConcurrencyControllerTest#givenTwoConcurrentBookings_whenSameSeatRequested_thenExactlyOneSucceeds`
- Run app locally: `./mvnw spring-boot:run`
- Swagger UI: `/swagger-ui.html`; OpenAPI spec: `/v3/api-docs`

**Tests require Docker** — they spin up a real Postgres via Testcontainers (`TestcontainersConfiguration`). No H2/in-memory fallback exists; ask before disabling this.

## Architecture

Spring Boot 4 (Java 17, Maven) REST backend for movie ticket booking, no UI/auth/payments. Layered `Controller -> Service -> Repository` (Spring Data JPA) per feature package (`movie`, `showtime`, `booking`), Postgres via Flyway migrations (`src/main/resources/db/migration`).

**Domain model**: `Movie` -> `Showtime` (FK) -> `Seat` (FK, one row per seat pre-created at showtime creation) -> `Booking` (Seat carries a nullable `booking_id`, not a join table, since a seat belongs to at most one booking). `SeatStatus` is intentionally two-valued (`AVAILABLE`/`BOOKED`) — no `RESERVED`/hold state exists because there's no operation in scope that produces one. `Booking` has no status field: it either exists (committed) or doesn't (rolled back).

**Concurrency control is the core design point** (`SeatRepository.markBooked`, used by `BookingService.bookSeats`): booking issues a conditional `UPDATE seat SET status='BOOKED' ... WHERE id=:id AND status='AVAILABLE'` per seat inside one `@Transactional` method, rather than JPA optimistic locking (`@Version`). The UPDATE takes the Postgres row lock, so a concurrent booking of the same seat blocks until the first commits, then affects 0 rows. Any seat updating 0 rows throws `SeatUnavailableException` (mapped to `409` by `BookingExceptionHandler`), rolling back the whole transaction — true all-or-nothing, no retry loop. Don't reintroduce `@Version`/optimistic locking here; there's no other write path to `Seat` that would need it.

**Testing seam** (single seam, full-stack only — see `.spec/movie-booking/plan.md`): all tests extend `AbstractIntegrationTest`, which boots the app on a random port (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) with a real Testcontainers Postgres, and drive it via `TestRestTemplate` — real HTTP over real threads/connections, not `MockMvc`, because the concurrency test needs actual concurrent transactions. No mocking of service/repository layers; assertions land on HTTP status + response body + read-back state (e.g. `GET /showtimes/{id}` after booking), never on internal method calls. `AbstractIntegrationTest` pins `spring.flyway.target=1` so tests only get the schema migration (`V1`), never the demo-data seed migration (`V2`) — tests build their own fixtures via repositories.

**Endpoints**:
- `GET /movies`
- `GET /movies/{movieId}/showtimes`
- `GET /showtimes/{showtimeId}` — includes every seat's id/label/status
- `POST /showtimes/{showtimeId}/bookings` — body `{ "seatIds": [...] }`; `201` with booking+seats, or `409` naming the unavailable seats (all-or-nothing)

## Spec-driven workflow

This repo tracks feature work under `.spec/<slug>/` (`spec.md`, `plan.md`, `tasks.md`) and `tickets/<TICKET-ID>.yaml`. Check these before starting new work on a ticket — they record the accepted scope, non-goals, and key design decisions (e.g. why `@Version` was rejected, why `SeatStatus` has only two values) that shouldn't be silently re-litigated.
