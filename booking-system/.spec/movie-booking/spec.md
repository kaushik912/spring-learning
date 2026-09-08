---
status: draft
---
# Movie ticket booking backend

## Problem

People need to book movie tickets over an API. Naive implementations let
two concurrent requests book the same seat, so a seat gets double-sold or a
booking claims seats that were already taken by someone else. We need a
backend that allocates seats safely under concurrent load.

## Goal

A Spring Boot backend (REST, no UI) that models the standard booking domain
— movies, showtimes with a seat map, seat status, and bookings — and
guarantees that a given seat can be booked by at most one customer, even
when many booking requests arrive at the same time.

## Non-goals

- No authentication / authorization / user accounts
- No payment processing
- No frontend / UI (backend only)
- No multi-theater chains or complex pricing/offers
- No seat holds with expiry timers or waiting lists

## Acceptance criteria

- A client can list movies and showtimes.
- A client can query a showtime and see the status (available/reserved/booked) of each seat.
- A client can book one or more seats on a showtime.
- Two concurrent booking requests for the same seat resolve to exactly one
  success and one failure; the seat is never double-booked (asserted by
  read-back: exactly one booking owns the seat).
- A booking that tries to include an already-booked seat fails and
  books nothing (all-or-nothing).
- Seat availability read-backs reflect committed bookings.
- All booking allocation must be safe under concurrency (optimistic locking
  or DB-level constraint, transactions, with a concurrency test proving it).
