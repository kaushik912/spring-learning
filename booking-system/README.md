# booking-system

Spring Boot 4 (Java 17, Maven) REST backend for movie ticket booking. No UI, no auth, no payments — a learning/demo project focused on correct concurrency handling for seat booking.

## Functionality

- List movies, list showtimes for a movie, view showtime seat map, book seats on a showtime.
- Booking is **all-or-nothing**: if any requested seat is already booked, the whole request fails with `409` and no seats are reserved.
- No booking cancellation, no auth, no payment flow — out of scope by design.

### Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/movies` | List all movies |
| GET | `/movies/{movieId}/showtimes` | List showtimes for a movie |
| GET | `/showtimes/{showtimeId}` | Showtime detail, including every seat's id/label/status |
| POST | `/showtimes/{showtimeId}/bookings` | Body `{ "seatIds": [...] }`. `201` with booking+seats, or `409` naming the unavailable seats |

Swagger UI: `/swagger-ui.html` — OpenAPI spec: `/v3/api-docs` (springdoc-openapi).

## Architecture

Layered `Controller -> Service -> Repository` (Spring Data JPA), one package per feature: `movie`, `showtime`, `booking`. Postgres via Flyway migrations (`src/main/resources/db/migration`).

**Domain model**: `Movie` -> `Showtime` (FK) -> `Seat` (FK, one row per seat pre-created at showtime creation) -> `Booking`. `Seat` carries a nullable `booking_id` column (not a join table) since a seat belongs to at most one booking. `SeatStatus` is intentionally two-valued (`AVAILABLE`/`BOOKED`) — there's no `RESERVED`/hold state because no in-scope operation produces one. `Booking` has no status field: it either exists (committed) or doesn't (rolled back). Schema: `booking-system/src/main/resources/db/migration/V1__init_schema.sql`.

**Concurrency control is the core design point.** `BookingService.bookSeats` (`@Transactional`) calls `SeatRepository.markBooked` per seat — a conditional `UPDATE seat SET status='BOOKED' ... WHERE id=:id AND status='AVAILABLE'`. The UPDATE takes the Postgres row lock, so a concurrent booking of the same seat blocks until the first transaction commits, then affects 0 rows. Any seat updating 0 rows throws `SeatUnavailableException`, mapped to `409` by `BookingExceptionHandler`, rolling back the whole transaction — true all-or-nothing, no retry loop. This is deliberately **not** JPA optimistic locking (`@Version`); there's no other write path to `Seat` that would need it, so don't reintroduce `@Version` here.

## Key classes / modules

| Package | Class | Responsibility |
|---|---|---|
| `com.example.booking` | `BookingSystemApplication` | `@SpringBootApplication` entry point |
| `movie` | `MovieController` / `MovieService` / `MovieRepository` | `GET /movies` — thin passthrough to `findAll()` |
| `showtime` | `ShowtimeController` / `ShowtimeService` / `ShowtimeRepository` / `SeatRepository` | List showtimes for a movie; showtime detail with seat map (`ShowtimeService.getShowtimeDetail` 404s via `ResponseStatusException` if not found) |
| `showtime` | `SeatRepository.markBooked` | The conditional CAS UPDATE that makes concurrent booking safe (see above) |
| `booking` | `BookingController` | `POST /showtimes/{showtimeId}/bookings` |
| `booking` | `BookingService.bookSeats` | Creates the `Booking` row, then loops seats calling `markBooked`; throws `SeatUnavailableException` with the list of conflicting seat ids if any fail |
| `booking` | `BookingExceptionHandler` | `@RestControllerAdvice` — maps `SeatUnavailableException` to `409` with `{message, unavailableSeatIds}` |
| `booking` | `SeatUnavailableException` | Carries the list of seat ids that lost the race |

Call chain for the booking write path: `BookingController.bookShowtime` -> `BookingService.bookSeats` -> `SeatRepository.markBooked` (per seat, in one transaction) -> `BookingExceptionHandler` (on conflict).

## Testing

All tests extend `AbstractIntegrationTest`: boots the full app on a random port (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) against a real Testcontainers Postgres, driven via `TestRestTemplate` (real HTTP over real threads — needed for the concurrency test to exercise actual concurrent transactions). No mocking of service/repository layers; assertions land on HTTP status + response body + read-back state, never on internal method calls. `AbstractIntegrationTest` pins `spring.flyway.target=1` so tests only get the schema migration (`V1`), never the demo-data seed (`V2`) — tests build their own fixtures via repositories.

**Tests require Docker** (Testcontainers spins up real Postgres) — no H2/in-memory fallback exists.

## Setup / run

```bash
# run all tests (requires Docker)
./mvnw test
# or
make test

# single test class / method
./mvnw test -Dtest=BookingConcurrencyControllerTest
./mvnw test -Dtest=BookingConcurrencyControllerTest#givenTwoConcurrentBookings_whenSameSeatRequested_thenExactlyOneSucceeds

# run the app locally
./mvnw spring-boot:run
```

Flyway runs `V1__init_schema.sql` then `V2__seed_demo_data.sql` on startup against whatever Postgres `spring.datasource.*` resolves to (not set in `application.properties` — provide via env/profile, or use Testcontainers for tests).

### Running the app locally without Docker

`spring.datasource.*` isn't set in `application.properties`, so `./mvnw spring-boot:run` fails with `Failed to configure a DataSource` unless a real Postgres is reachable. Tests get one automatically via Testcontainers, but running the app itself needs one set up by hand. One-time setup with a native (non-Docker) Postgres:

```bash
# 1. Install Postgres (Ubuntu/WSL2) if not already present
sudo apt-get update && sudo apt-get install -y postgresql postgresql-contrib

# 2. Confirm it's running (installs typically auto-start the cluster)
pg_lsclusters

# 3. Create a dedicated db + user (one-time)
sudo -u postgres psql -c "CREATE USER booking_user WITH PASSWORD 'booking_pass';" \
                       -c "CREATE DATABASE booking_system OWNER booking_user;"
```

Then run the app with datasource creds passed as env vars — **never** commit them into `application*.properties` (see security rules):

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/booking_system"
export SPRING_DATASOURCE_USERNAME="booking_user"
export SPRING_DATASOURCE_PASSWORD="booking_pass"
./mvnw spring-boot:run
```

Flyway applies `V1` + `V2` (schema + seed data) automatically on first boot. Verify with `curl http://localhost:8080/movies`. Use a different password for anything beyond a throwaway local dev db.

## Repo conventions

- Spec-driven workflow: feature work is tracked under `.spec/<slug>/` (`spec.md`, `plan.md`, `tasks.md`) and `tickets/<TICKET-ID>.yaml`. Check these before starting new work — they record accepted scope, non-goals, and key design decisions (e.g. why `@Version` was rejected) that shouldn't be silently re-litigated.
- See `CLAUDE.md` at the repo root for the canonical version of this architecture summary (kept in sync with this README).
