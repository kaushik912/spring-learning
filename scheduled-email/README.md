# Scheduled email on multiple instances

**Problem.** A Spring `@Scheduled` job emails 1000 users. The app runs as 2+ instances, so every instance fires the
cron. Each user must get **one** email, even with provider failures, crashes and overlapping runs.

Three demos of the same problem, all on Postgres (docker compose) with Testcontainers tests and a 2+ JVM run script.

| Project | Design | Read it for |
|---|---|---|
| [`scheduled-email-simple-jdbc-demo/`](scheduled-email-simple-jdbc-demo/) | ShedLock + one `emails_sent` status table (`PENDING`/`SENT`/`FAILED`, 3 attempts), sweeper retries, support `resend` endpoint. **Plain SQL (JdbcTemplate).** | **Start here.** Smallest, interview-sized |
| [`scheduled-email-simple-demo/`](scheduled-email-simple-demo/) | The same design with **Spring Data JPA** | Comparing JPA vs SQL for a concurrency-sensitive job (`Persistable`, JPQL updates) |
| [`scheduled-email-dedup-demo/`](scheduled-email-dedup-demo/) | The heavy design: outbox queue, many worker threads per instance, `FOR UPDATE SKIP LOCKED`, reaper, `kill -9` demo. Also has the full comparison of every locking/dedup option (Redis lock, pessimistic lock, Quartz, queues...) | When it must scale out, and the "what are all the options" discussion |

## How they relate

```
needs only: one runner per tick, no duplicate rows, retry failures, replay one user
        ──▶ simple (JDBC or JPA)

needs also: spread sends across many instances/threads, strong no-double-send without relying on
            the provider's idempotency key
        ──▶ dedup-demo (outbox + atomic claim + reaper)
```

Layers used across all three (the heavy one uses all, the simple ones use the first four):
1. **ShedLock** so one instance runs each job per tick (avoids wasted duplicate work, not a correctness guarantee).
2. **Primary / unique key** on `(run_id, user_id)` so each user exists once per run.
3. **Status + attempts** so failures are retried and give up after N.
4. **Idempotency key to the provider** (`runId:userId:rN`) so an overlap or crash-then-retry doesn't send twice.
5. *(heavy only)* atomic claim (`SKIP LOCKED`) and a reaper for crashed workers.

## Running them

Each project is standalone (own `mvnw`, `docker-compose.yml`, ports) so they can run side by side:

| Project | Postgres | Apps | Run |
|---|---|---|---|
| `scheduled-email-dedup-demo` | 5433 | 8081.. | `./run-instances.sh 3` |
| `scheduled-email-simple-demo` (JPA) | 5434 | 8091, 8092 | `./run-instances.sh` |
| `scheduled-email-simple-jdbc-demo` | 5435 | 8093, 8094 | `./run-instances.sh` |

All need Docker and Java 21. `./mvnw test` in any project runs its tests (Testcontainers starts Postgres itself).
