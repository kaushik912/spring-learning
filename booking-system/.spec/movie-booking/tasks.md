---
status: approved
---
# Movie ticket booking backend — Tasks

- [x] Scaffold Spring Boot project via `spring init` (Maven, Java): web, data-jpa, postgresql, flyway, testcontainers, validation — used 4.0.8, since Boot 3.x is gone from start.spring.io (EOL); confirmed with user
- [x] Add Testcontainers-Postgres test base config (`@ServiceConnection`) and Flyway `V1__init_schema.sql` (movie, showtime, seat, booking tables — FKs, `NOT NULL`, `UNIQUE(showtime_id, seat_label)` on seat)
- [x] Write failing test: `GET /movies` returns all movies (read-back after inserting fixtures via repository)
- [x] Implement Movie entity/repository/service/controller to pass it
- [x] Write failing test: `GET /movies/{id}/showtimes` returns only that movie's showtimes
- [x] Implement Showtime entity/repository/service/controller to pass it
- [x] Write failing test: `GET /showtimes/{id}` returns the seat map with each seat's id/label/status
- [x] Implement Seat entity/repository and showtime-detail endpoint to pass it
- [x] Write failing test: `POST /showtimes/{id}/bookings` with available seats returns 201 with the booking + seats, and a follow-up `GET /showtimes/{id}` read-back shows those seats `BOOKED`
- [x] Implement Booking entity + booking service (conditional `UPDATE ... WHERE status='AVAILABLE'` per seat, single `@Transactional` method) + controller to pass it
- [x] Write failing test: booking a set that includes an already-booked seat returns 409 and books nothing — read-back shows the other requested seats still `AVAILABLE`
- [x] Implement rollback/error-mapping (`@ControllerAdvice`) to pass it
- [x] Write failing test: two concurrent `POST` bookings for the same seat (two threads, `CountDownLatch`-synchronized start) resolve to exactly one 201 and one 409; read-back shows exactly one booking owns the seat
- [x] Verify it passes against the existing conditional-UPDATE implementation (add locking/isolation fixes only if the test reveals a gap) — passed as-is; only had to expose `bookingId` on SeatResponse for the read-back assertion
- [x] Add Flyway seed migration (`V2__seed_demo_data.sql`) with sample movies/showtimes/seats for manual/local exploration
- [ ] Wire `springdoc-openapi-starter-webmvc-ui` (2.8.6) dependency + `@Tag`/`@Operation` annotations on controllers
- [ ] Write failing test: `/swagger-ui.html` and `/v3/api-docs` return 200/3xx
- [ ] Verify it passes
- [ ] Write `Makefile` with a `test` target running `./mvnw test` (Testcontainers starts Postgres automatically — no separate DB setup required)
