# Scheduled email on multiple instances

**Problem.** A Spring `@Scheduled` job emails 1000 users. The app runs as 2+ instances, so every instance fires the
cron. Each user must get **one** email, even with provider failures, crashes and overlapping runs.

Four demos of the same problem, all on Postgres (docker compose) with Testcontainers tests and a 2+ JVM run script.
The Kafka one adds a Kafka broker.

| Project | Design | Read it for |
|---|---|---|
| [`scheduled-email-simple-jdbc-demo/`](scheduled-email-simple-jdbc-demo/) | ShedLock + one `emails_sent` status table (`PENDING`/`SENT`/`FAILED`, 3 attempts), sweeper retries, support `resend` endpoint. **Plain SQL (JdbcTemplate).** | **Start here.** Smallest, interview-sized |
| [`scheduled-email-simple-demo/`](scheduled-email-simple-demo/) | The same design with **Spring Data JPA** | Comparing JPA vs SQL for a concurrency-sensitive job (`Persistable`, JPQL updates) |
| [`scheduled-email-dedup-demo/`](scheduled-email-dedup-demo/) | The heavy design: outbox queue, many worker threads per instance, `FOR UPDATE SKIP LOCKED`, reaper, `kill -9` demo. Also has the full comparison of every locking/dedup option (Redis lock, pessimistic lock, Quartz, queues...) | When it must scale out, and the "what are all the options" discussion |
| [`scheduled-email-kafka-demo/`](scheduled-email-kafka-demo/) | **Kafka as the queue**: ShedLock publisher produces one message per user (key = userId), a consumer group sends, error handler retries then a dead-letter topic, resend = new message with a new idempotency key. `kill -9` demo | Scaling out **without hand-rolling** claim/reaper logic; the "how would you do it with Kafka" answer |

## How they relate

```
needs only: one runner per tick, no duplicate rows, retry failures, replay one user
        ──▶ simple (JDBC or JPA)

needs also: spread sends across many instances/threads, strong no-double-send without relying on
            the provider's idempotency key
        ──▶ dedup-demo (outbox + atomic claim + reaper), you build the queue

needs also: the same scale-out, and you already run Kafka (or accept it)
        ──▶ kafka-demo (partitions + rebalance + dead-letter topic), the broker is the queue
```

Layers used across the demos:
1. **ShedLock** so one instance runs each job per tick (avoids wasted duplicate work, not a correctness guarantee).
2. **Primary / unique key** on `(run_id, user_id)` so each user exists once per run (Kafka demo: dedup check on the
   consumer, `(run, user, resend_count)` as the key).
3. **Status + attempts** so failures are retried and give up after N (Kafka demo: error handler + dead-letter topic).
4. **Idempotency key to the provider** (`runId:userId:rN`) so an overlap or crash-then-retry doesn't send twice.
5. *(heavy only)* atomic claim (`SKIP LOCKED`) and a reaper for crashed workers. Kafka does both for you.

## Running them

Each project is standalone (own `mvnw`, `docker-compose.yml`, ports) so they can run side by side:

| Project | Postgres | Apps | Run |
|---|---|---|---|
| `scheduled-email-dedup-demo` | 5433 | 8081.. | `./run-instances.sh 3` |
| `scheduled-email-simple-demo` (JPA) | 5434 | 8091, 8092 | `./run-instances.sh` |
| `scheduled-email-simple-jdbc-demo` | 5435 | 8093, 8094 | `./run-instances.sh` |
| `scheduled-email-kafka-demo` | 5436 (+ Kafka 9094) | 8095, 8096 | `./run-instances.sh` |

All need Docker and Java 21. `./mvnw test` in any project runs its tests (Testcontainers starts Postgres, and Kafka for
the Kafka demo, itself).
