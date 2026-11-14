# Scheduled email on multiple instances: the simple version

Same problem as [`../scheduled-email-dedup-demo`](../scheduled-email-dedup-demo) (a `@Scheduled` job emails 1000
users, app runs as 2+ instances, each user must get **one** email), but without the queue, worker threads and
reaper. Two small scheduled jobs plus one status table.

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
| Primary key (`existsById` check, then insert; a duplicate-key error is caught) | Seeding is repeatable; each (run, user) exists once | `EmailService.seed`, `EmailRecord` |
| `send` job | Seed today's rows, send everything `PENDING` | `EmailJobs.send` |
| `sweep` job | Retry `FAILED` rows with `attempts < 3`, any day | `EmailJobs.sweep` |
| Idempotency key `runId:userId:rN` | Provider drops a repeat of the same key, so an overlap or a crash-then-retry doesn't send twice | `MockEmailClient`, `Row.idempotencyKey` |
| `resend` endpoint | Support re-queues one user ("I never got it") | `AdminController` |

## JPA notes (no hand-written SQL except the tiny update queries)

Spring Data JPA + Hibernate. `schema.sql` creates the tables; `ddl-auto=validate` only checks the entities match.

| File | What it is |
|---|---|
| `EmailRecord` + `EmailKey` + `EmailStatus` | The `emails_sent` entity, its composite key `(runId, userId)`, and the status enum |
| `EmailRecordRepository` | Reads are **derived from the method name** (`countByIdRunIdAndStatus(...)` = count rows where `id.runId` and `status` match). Updates are 3 short `@Query` JPQL statements |
| `ProviderDelivery`/`ProviderCall` + repos | The mock provider's tables, same style |

Three JPA details worth knowing:
1. **`EmailRecord implements Persistable`.** We assign the key ourselves, so `repo.save()` would normally think
   "id is set, so it must exist" and do a *merge* (SELECT then UPDATE). In a race that could reset a `SENT` row to
   `PENDING`. `isNew() = true` makes `save()` a plain INSERT, and the primary key rejects a duplicate.
2. **Updates are `@Modifying @Query` instead of load-modify-save.** With overlapping runners, "load row, change
   status, save" lets a slow runner overwrite a fast runner's result. A conditional `update ... where status <> SENT`
   is atomic in the database. (`markFailed` can never overwrite `SENT`.)
3. **Caught duplicate-key errors.** `seed` and the mock provider insert and catch `DataIntegrityViolationException`
   when another runner got there first. Hibernate logs each one, so `logging.level.org.hibernate.orm.jdbc.error=off`
   keeps the log readable.

Trade-off vs hand-written SQL: JPA is one round trip per row here (`existsById` + insert), so the test runs
about three times more slowly than the JdbcTemplate version, which lives in
[`../scheduled-email-simple-jdbc-demo`](../scheduled-email-simple-jdbc-demo) (same design, plain SQL, side-by-side
comparison table in its README). Fine for 1000 users.

## How to run

Needs Docker (Postgres 17) and Java 21.

```bash
./mvnw test              # Testcontainers Postgres, 3 tests
./run-instances.sh       # compose Postgres + 2 JVMs (:8091, :8092), waits, demos resend
docker compose down -v   # cleanup
```

By hand:
```bash
docker compose up -d --wait postgres
./mvnw spring-boot:run                                     # :8091, cron every 30s (send) / 10s (sweep)
curl localhost:8091/admin/emails/report                    # counts, dead users, provider stats
curl -X POST 'localhost:8091/admin/emails/2026-11-14/24/resend?by=alice'   # re-queue one user
```

Knobs (`application.properties`, `demo.*`): `user-count`, `max-attempts`, `provider-failure-rate`, `send-cron`, `sweep-cron`.
(Real life: `send-cron` would be daily, e.g. `0 0 9 * * *`.)

## What the tests / run show

- **Overlapping runners** (simulates an expired lock): two threads send the same 1000 rows at once. Hundreds of
  duplicate calls hit the provider (507 in one run), **all ignored** via the idempotency key. About 97% end SENT,
  the rest give up after 3 tries, and every SENT user has exactly one delivery.
- **Resend of a SENT user** gets a new key (`...:r1`), so the provider delivers it again instead of dropping it
  as a repeat.
- **Dead user** (gave up after 3 failures): after "fixing the provider", the sweeper still ignores it. `resend`
  gives it a fresh 3 attempts, and only that user is retried.
- **2 real JVMs**: 993/1000 SENT, 7 dead, 248 provider 503s absorbed by retries. One instance won each send tick
  (ShedLock). Resending dead user 168 delivered it; the other 6 stayed dead.

## Why this is enough (and where it isn't)

| Situation | What happens |
|---|---|
| Provider 503 | Row stays `FAILED`, sweeper retries up to 3 times |
| Instance crashes mid-job | Rows not yet marked stay `PENDING`; the next `send` tick sends them (also from older days) |
| Crash after provider accepted, before marking `SENT` | Retry resends, provider ignores it (same key) |
| ShedLock expires, 2 runners overlap | Both may send; provider ignores the repeat (same key) |
| Customer complains | `resend` endpoint |

Known limits (the reason the full demo exists):
- **Overlap safety depends on the provider's idempotency key.** Both runners read the same `PENDING` list
  (check-then-act). If your provider has no idempotency key, an overlap or a crash-after-send can send twice.
  The fully safe fix is an atomic claim (`PENDING → CLAIMED`) plus a reaper = the full demo.
- **Single sender.** One instance sends everything sequentially. Fine for ~thousands; not for millions
  (use the outbox + `SKIP LOCKED` workers, or a queue).
- **Providers remember keys for a limited time** (often 24h). A retry after that window is treated as new.
- **Admin endpoints have no auth.** Add it before using them for real.

## Choice summary

| Need | Simple version (this) | Full version |
|---|---|---|
| Only one instance runs the job | ShedLock | ShedLock |
| No duplicate rows | Primary key | Unique constraint |
| Retries on failure | Sweeper, status + attempts | Workers + reaper |
| No double send on overlap | Provider idempotency key | Atomic claim + key |
| Spread work over instances | No | `SKIP LOCKED` workers |
| Replay one user | `resend` endpoint | n/a |
