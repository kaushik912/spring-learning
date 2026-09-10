---
name: booking-system-structure
description: Package layout, entry point, and key classes of booking-system (movie ticket booking REST API), for fast doc updates without re-exploring
metadata:
  type: project
---

Spring Boot 4 / Java 17 / Maven REST backend for movie ticket booking (no UI/auth/payments).
Root: `/home/kaush/github_projs/spring-learning/booking-system`. Documented at
`booking-system/README.md` (dev-facing) — canonical architecture summary also lives in
`booking-system/CLAUDE.md` (kept in sync manually, not auto-generated).

**Layout** — one package per feature under `src/main/java/com/example/booking/`:
- `BookingSystemApplication.java` — `@SpringBootApplication` entry point.
- `movie/` — Movie, MovieController (`GET /movies`), MovieService, MovieRepository, MovieResponse.
- `showtime/` — Showtime, Seat, SeatStatus (enum: AVAILABLE/BOOKED only), ShowtimeController
  (`GET /movies/{id}/showtimes`, `GET /showtimes/{id}`), ShowtimeService, ShowtimeRepository,
  SeatRepository (has the concurrency-critical `markBooked` conditional UPDATE query).
- `booking/` — Booking, BookingController (`POST /showtimes/{id}/bookings`), BookingService
  (`bookSeats` — the core transactional write path), BookingRepository, BookingRequest/Response,
  SeatUnavailableException, BookingExceptionHandler (`@RestControllerAdvice`, maps to 409).
- `src/main/resources/db/migration/` — Flyway: `V1__init_schema.sql` (schema),
  `V2__seed_demo_data.sql` (demo data, skipped in tests).
- `src/test/java/.../support/AbstractIntegrationTest.java` — base class all tests extend;
  full-stack HTTP tests via TestRestTemplate + Testcontainers Postgres (Docker required, no
  H2 fallback); pins `spring.flyway.target=1` so tests don't get V2 seed data.
- `.spec/movie-booking/{spec,plan,tasks}.md` and `tickets/TICKET-001.yaml` — spec-driven
  workflow tracking; check before new feature work.

**Core design point** (concurrency): `BookingService.bookSeats` loops seats calling
`SeatRepository.markBooked` (conditional `UPDATE ... WHERE status='AVAILABLE'`) inside one
`@Transactional` method — DB row lock does the concurrency control, not JPA `@Version`. 0 rows
updated -> `SeatUnavailableException` -> whole txn rolls back -> 409 naming unavailable seats.

**Why this matters for docs:** this design decision (no `@Version`, two-valued SeatStatus, no
booking status field) is intentional and explained in booking-system/CLAUDE.md — don't describe
it as a gap or suggest adding optimistic locking without checking there first.
**How to apply:** when re-documenting or extending booking-system, read CLAUDE.md first (it's
already accurate and detailed as of 2026-09-10), diff against current code, and update
README.md/CLAUDE.md together rather than re-deriving architecture from scratch.
