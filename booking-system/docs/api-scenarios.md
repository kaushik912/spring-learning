# API Scenarios

Tracks regression scenarios explored for the booking-system API, one `##` section per category. Status is `not yet tested` until a scenario has a test implementing it, then becomes `implemented in <path>`.

## Showtimes

| ID | Scenario | Why it matters | Status |
|---|---|---|---|
| SCN-001 | `GET /showtimes/{id}` for an id that does not exist returns 404 | Confirms the `ResponseStatusException` 404 path fires as designed; no existing test exercised it | implemented in booking-system/bruno/SCN-001-showtime-not-found.bru |
| SCN-002 | `GET /movies/{movieId}/showtimes` for a movie id that does not exist returns 200 with an empty list, not 404 | Undocumented behavior: movie existence is never checked, so "movie not found" and "movie has no showtimes" look identical to a client | implemented in booking-system/bruno/SCN-002-showtimes-for-missing-movie.bru |
| SCN-003 | `GET /showtimes/{id}` with a non-numeric id (e.g. `abc`) returns 400, not a raw server error | Confirms Spring's default path-variable type-mismatch handling doesn't leak a stack trace | implemented in booking-system/bruno/SCN-003-showtime-id-non-numeric.bru |

## Bookings

| ID | Scenario | Why it matters | Status |
|---|---|---|---|
| SCN-004 | `POST /showtimes/{id}/bookings` with the `seatIds` field omitted entirely should return 400 | No validation exists on `BookingRequest.seatIds`; the service currently iterates a `null` list, an unvalidated-input crash risk rather than a clean 400. **Currently fails** — app returns 500, not 400 (bug candidate, tracked separately) | implemented in booking-system/bruno/SCN-004-booking-missing-seatids.bru |
| SCN-005 | `POST /showtimes/{id}/bookings` with `seatIds: []` (empty array) should return 400 | An empty booking with nothing reserved should be rejected, not silently accepted as an orphan record with no seats. **Currently fails** — app returns 201 with zero seats, not 400 (bug candidate, tracked separately) | implemented in booking-system/bruno/SCN-005-booking-empty-seatids.bru |
| SCN-006 | `POST /showtimes/{id}/bookings` against a `showtimeId` that does not exist should return 404 | The service never checks the showtime exists before booking seats, so a nonexistent showtime currently looks identical to a real seat conflict (409). Should be a distinct 404, matching the GET endpoint. **Currently fails** — app returns 409, not 404 (bug candidate, tracked separately) | implemented in booking-system/bruno/SCN-006-booking-showtime-not-found.bru |
