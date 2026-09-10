---
name: project-booking-system-context
description: Known bug candidates found in booking-system's booking/showtime endpoints during scenario-grilling, tracked via Bruno tests that intentionally fail until fixed.
metadata:
  type: project
---

`booking-system` (Spring Boot 4, Java 17) had its first scenario-griller pass on 2026-09-10, covering the Showtimes and Bookings categories. Six scenarios recorded in `booking-system/docs/api-scenarios.md` (SCN-001..SCN-006), all implemented as Bruno tests under `booking-system/bruno/`.

Three of the six are known bug candidates, written to assert correct behavior and so currently FAIL against the app (see [[feedback-bug-documenting-tests]] for why they're written this way):
- SCN-004: `POST /showtimes/{id}/bookings` with `seatIds` omitted — `BookingService.bookSeats` iterates a null list, likely a raw 500; test asserts 400.
- SCN-005: `POST /showtimes/{id}/bookings` with `seatIds: []` — currently accepted, creates a booking with zero seats (201); test asserts 400.
- SCN-006: `POST /showtimes/{id}/bookings` against a nonexistent `showtimeId` — service never checks showtime existence, so it looks like a seat conflict (409) instead of a missing-resource error; test asserts 404.

**Why:** none of these are validated today — `BookingRequest.seatIds` has no `@NotEmpty`/`@NotNull`, and `BookingService.bookSeats` never checks the showtime exists before touching seats. `spring-boot-starter-validation` is already a dependency, so adding `@NotEmpty` to `BookingRequest.seatIds` and a showtime-existence check in `BookingService` would fix all three.

**How to apply:** these fixes are intentionally deferred to a separate Jira ticket per the user's instruction, not bundled into the scenario-grilling pass. If asked to fix booking-system bugs later, these three are already scoped and reproducible via `booking-system/bruno/SCN-004..006-*.bru`. SCN-001..003 (Showtimes category) passed as-is; SCN-002 (movie-not-found returns empty list) is documented as an accepted ambiguity, not a bug.
