# Scheduled email on multiple instances: the simple version (JdbcTemplate)

A `@Scheduled` job emails 1000 users, the app runs as 2+ instances, and each user must get **one** email.
This is the small solution: two scheduled jobs plus one status table, with plain SQL via `JdbcTemplate`.

Sibling projects (same problem, same behaviour):
- [`../scheduled-email-simple-demo`](../scheduled-email-simple-demo): this exact design written with **Spring Data JPA**.
- [`../scheduled-email-dedup-demo`](../scheduled-email-dedup-demo): the heavy design (outbox, `SKIP LOCKED` workers, reaper).

## How it works

One table, `emails_sent`, primary key `(run_id, user_id)`:

```
seed ──▶ PENDING ──send ok──▶ SENT
            │
            └─send fails──▶ FAILED (attempts+1) ──sweeper retries──▶ SENT
                                  │                                   or FAILED again
                                  └─ attempts == 3 → "dead": sweeper stops
support "resend" ──▶ FAILED, attempts = 0, resend_count + 1 ──▶ sweeper sends it again
```

| Piece | What it does | Code |
|---|---|---|
| `@SchedulerLock` on both jobs | Only one instance runs each job per tick (avoids wasted duplicate work) | `EmailJobs` |
| Primary key + `ON CONFLICT DO NOTHING` | Seeding is repeatable; each (run, user) exists once | `EmailService.seed` |
| `send` job | Seed today's rows, send everything `PENDING` | `EmailJobs.send` |
| `sweep` job | Retry `FAILED` rows with `attempts < 3`, any day | `EmailJobs.sweep` |
| Idempotency key `runId:userId:rN` | Provider drops a repeat of the same key, so an overlap or a crash-then-retry doesn't send twice | `MockEmailClient`, `Row.idempotencyKey` |
| `resend` endpoint | Support re-queues one user ("I never got it") | `AdminController` |

### The SQL you need to know (it's short)

- `INSERT ... VALUES (...) ON CONFLICT (run_id, user_id) DO NOTHING`: insert, but silently skip if that primary
  key already exists. `jdbc.update` returns 1 if it inserted and 0 if it skipped.
- `UPDATE emails_sent SET ... WHERE run_id = ? AND user_id = ? AND status <> 'SENT'`: a conditional update, so a slow
  runner can never overwrite another runner's `SENT`.
- Everything else is plain `SELECT ... WHERE`, `COUNT(*)`, `GROUP BY`.

## How to run

Needs Docker (Postgres 17) and Java 21.

```bash
./mvnw test              # Testcontainers Postgres, 3 tests
./run-instances.sh       # compose Postgres + 2 JVMs (:8093, :8094), waits, demos resend
docker compose down -v   # cleanup
```

By hand:
```bash
docker compose up -d --wait postgres
./mvnw spring-boot:run                                     # :8093, cron every 30s (send) / 10s (sweep)
curl localhost:8093/admin/emails/report                    # counts, dead users, provider stats
curl -X POST 'localhost:8093/admin/emails/2026-11-14/455/resend?by=alice'   # re-queue one user
```

Knobs (`application.properties`, `demo.*`): `user-count`, `max-attempts`, `provider-failure-rate`, `send-cron`, `sweep-cron`.
(Real life: `send-cron` would be daily, e.g. `0 0 9 * * *`.) Ports: Postgres 5435, apps 8093/8094, so it can run
next to the other email demos.

## What the tests / run show

- **Overlapping runners** (simulates an expired lock): two threads send the same 1000 rows at once. Hundreds of
  duplicate calls hit the provider (479 in one run), **all ignored** via the idempotency key. About 96-97% end
  SENT, the rest give up after 3 tries, and every SENT user has exactly one delivery.
- **Resend of a SENT user** gets a new key (`...:r1`), so the provider delivers it again instead of dropping it
  as a repeat.
- **Dead user** (gave up after 3 failures): after "fixing the provider", the sweeper still ignores it. `resend`
  gives it a fresh 3 attempts, and only that user is retried.
- **2 real JVMs**: 994/1000 SENT, 6 dead, 253 provider 503s absorbed by retries. Each send tick ran on one
  instance (ShedLock). Resending dead user 455 delivered it; the other 5 stayed dead.

## Why this is enough (and where it isn't)

| Situation | What happens |
|---|---|
| Provider 503 | Row stays `FAILED`, sweeper retries up to 3 times |
| Instance crashes mid-job | Rows not yet marked stay `PENDING`; the next `send` tick sends them (also from older days) |
| Crash after provider accepted, before marking `SENT` | Retry resends, provider ignores it (same key) |
| ShedLock expires, 2 runners overlap | Both may send; provider ignores the repeat (same key) |
| Customer complains | `resend` endpoint |

Known limits (the reason the heavy demo exists):
- **Overlap safety depends on the provider's idempotency key.** Both runners read the same `PENDING` list
  (check-then-act). If your provider has no idempotency key, an overlap or a crash-after-send can send twice.
  The fully safe fix is an atomic claim (`PENDING → CLAIMED`) plus a reaper = the heavy demo.
- **Single sender.** One instance sends everything sequentially. Fine for ~thousands; not for millions.
- **Providers remember keys for a limited time** (often 24h). A retry after that window is treated as new.
- **Admin endpoints have no auth.** Add it before using them for real.

## JdbcTemplate vs JPA (this project vs `../scheduled-email-simple-demo`)

Same behaviour, same tests. Differences:

| | JdbcTemplate (this) | JPA (`../scheduled-email-simple-demo`) |
|---|---|---|
| Java files in `src/main` | 5 | 13 (adds 3 entities, 3 repositories, key class, enum) |
| Test run time | ~17s | ~50s (one round trip per row: `existsById` + insert) |
| "Insert if absent" | `ON CONFLICT DO NOTHING` | `existsById` + insert + catch duplicate-key exception |
| Conditional updates | SQL in the service | JPQL `@Modifying @Query` |
| Needs to know | A little SQL | `Persistable` (else `save()` merges and can reset SENT to PENDING), JPQL, validate-vs-schema |

Rule of thumb: JPA for ordinary entity CRUD; plain SQL for concurrency-sensitive bits like upserts and conditional
updates. This job is mostly the second kind.

## Choice summary

| Need | Simple version (this) | Heavy version |
|---|---|---|
| Only one instance runs the job | ShedLock | ShedLock |
| No duplicate rows | Primary key | Unique constraint |
| Retries on failure | Sweeper, status + attempts | Workers + reaper |
| No double send on overlap | Provider idempotency key | Atomic claim + key |
| Spread work over instances | No | `SKIP LOCKED` workers |
| Replay one user | `resend` endpoint | n/a |
