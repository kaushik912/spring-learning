# Scheduled job on multiple instances: send each user exactly one email

**Problem.** A Spring `@Scheduled` job sends an email to 1000 users. The app runs as 2+ instances, so every
instance fires the cron and users get duplicate emails. We want **one email per user per run**.

**Reality check.** True exactly-once across "DB + network call" is impossible (the send and the DB write can't be
one atomic step). What we build is *at-least-once delivery + idempotency = effectively exactly-once*.

## What this demo implements (layered)

```
 every instance                         shared Postgres (docker compose)
┌──────────────────────┐   ShedLock     ┌──────────────────────────────────────┐
│ @Scheduled publisher │──(1 winner)──▶ │ email_outbox  UNIQUE(run_id,user_id) │  ◀─ layer 2
│  (all instances try) │  enqueue 1000  │   PENDING → CLAIMED → SENT           │
├──────────────────────┤                └──────────────────────────────────────┘
│ N worker threads     │ claim(SKIP LOCKED)   │ send(idempotencyKey)  │ mark SENT
│  (all instances)     │──────────────────────┴──────────┬────────────┘
├──────────────────────┤                                 ▼
│ reaper (ShedLock)    │  CLAIMED too long → PENDING   MockEmailProvider
└──────────────────────┘                               provider_delivered PK(idempotency_key) ◀─ layer 4
```

| Layer | Mechanism | Protects against | Code |
|---|---|---|---|
| 1 | **ShedLock** on the publisher | N instances all enqueueing (wasted work) | `EmailPublisher`, `ShedLockConfig` |
| 2 | **`UNIQUE(run_id, user_id)`** + `INSERT … ON CONFLICT DO NOTHING` (one insert per user, in a loop), `runId` derived from the schedule slot | ShedLock lock expiry / GC pause / clock skew / manual re-trigger → still no duplicate jobs | `OutboxRepository.enqueue` |
| 3 | **Atomic claim** `UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING …` | 2 workers grabbing the same user; skipping locked rows means no contention | `OutboxRepository.claimBatch` |
| 4 | **Idempotency key** (`runId:userId`) sent to provider | Worker dies after send, before marking SENT → retry resends | `MockEmailProvider` |
| 5 | **Reaper** re-queues stale `CLAIMED` rows | Worker crash/pause leaves a row stuck | `EmailWorkers.reap` |

"One publisher, many workers": ShedLock elects one publisher per tick; all instances run worker threads that
pull from the shared outbox (the outbox is the queue; swap for Kafka/SQS in prod, see choices below).

## How to run

Needs Docker (Postgres 17) and Java 21.

```bash
# 1) Test (Testcontainers Postgres): 5 racing publishers + 4 workers + injected 503s + simulated crashes, 1000 users
./mvnw test

# 2) Real multi-JVM run: compose Postgres + 3 instances on :8081-8083, races manual publishers,
#    then `kill -9` instance-8082 mid-run, waits for completion, prints report. NO_KILL=1 skips the kill.
./run-instances.sh 3
docker compose down -v      # cleanup
```

Or by hand (Postgres on localhost:5433; start the first instance and let it finish booting, it creates the schema):

```bash
docker compose up -d --wait postgres
./mvnw -q -DskipTests package
java -jar target/scheduled-email-dedup-demo-0.0.1-SNAPSHOT.jar --server.port=8081 &
sleep 8
java -jar target/scheduled-email-dedup-demo-0.0.1-SNAPSHOT.jar --server.port=8082 &
curl -X POST 'localhost:8081/api/publish?runId=r1'   # manual publish (no ShedLock)
curl -X POST 'localhost:8082/api/publish?runId=r1'   # second publisher: enqueued=0
curl 'localhost:8081/api/report?runId=r1'
```

Knobs in `application.properties` (`demo.*`): `user-count`, `workers`, `batch-size`, `provider-failure-rate`,
`crash-after-send-rate`, `claim-timeout-ms`, `publish-cron`.

### Result of a real run (Postgres, 3 JVMs, 1000 users, 5% provider 503s, 3% crash-after-send, one `kill -9`)

```
outboxByStatus:        SENT=1000
sentByInstance:        8081=352, 8082=303, 8083=345         <- work split (8082 was kill -9'd partway)
providerDeliveredUnique: 1000
providerCallsByOutcome: DELIVERED=1000, DUPLICATE_IGNORED=28, TRANSIENT_ERROR=47
exactlyOnceForAllUsers: true
```
- `DUPLICATE_IGNORED=28`: retries after crashes that **would have been duplicate emails**; the provider's
  idempotency key swallowed them. Delete layer 4 and you'd see duplicates.
- Reaper logs show stale `CLAIMED` rows re-queued after the kill (it can't separate them from the simulated
  crashes in the count, but both go through the same path).
- Publisher races: 3 manual publishers → total enqueued exactly 1000, the others got 0 (layer 2).
  Only one instance logged the cron-triggered run per tick (ShedLock, layer 1).

## All the choices for this problem

### A. Prevent N instances from firing (election / locking)
| Option | Pros | Cons / failure mode |
|---|---|---|
| **Redis lock + TTL** (`SET NX PX`) | Cheap, fast | TTL expiry while holder is paused (GC, VM freeze) → second holder runs, then first wakes and also sends. Needs fencing token; an email API can't check one. |
| **ShedLock** (JDBC/Redis/Mongo…) | One annotation; `lockAtMostFor`/`lockAtLeastFor`; survives node death | Same expiry caveat as any TTL lock (`lockAtMostFor` is a TTL). Mutual exclusion "mostly", not guaranteed. Fine for layer 1. |
| **DB pessimistic lock** (`SELECT … FOR UPDATE [SKIP LOCKED/NOWAIT]`) | Lock tied to the connection: no TTL race; dead node → DB releases | Row must exist; holding a tx open during network I/O exhausts pool; blocking (not skipping) lets 2nd instance send after 1st commits unless it re-checks status. Needs shared DB. |
| **DB advisory lock** (Postgres `pg_try_advisory_lock`) | Session-scoped, no table | Postgres-specific; session must stay alive for the whole job; poolers (pgbouncer tx mode) break it |
| **Quartz clustered (JDBC job store)** | Built-in misfire, clustering, per-job state | Heavy; its own tables/config; still at-least-once |
| **Leader election** (ZooKeeper/etcd/Consul/k8s Lease) | Real fencing via epoch/revision; leader stays leader | Extra infra; leader-pause problem remains unless the resource checks the epoch |
| **External trigger** (k8s CronJob, EventBridge/Cloud Scheduler → endpoint) | Instances stay stateless; platform schedules once | Platform can still double-fire/retry; still need idempotency |
| **Single scheduler instance** (`@Profile`/flag) | Trivial | SPOF; no failover |

### B. Make the work itself duplicate-proof (this is what actually gives correctness)
| Option | Notes |
|---|---|
| **Unique constraint claim** `UNIQUE(run_id,user_id)` + `INSERT … ON CONFLICT DO NOTHING` | Atomic, DB-enforced. **Used here.** Do *not* do check-then-insert (`SELECT` then `INSERT`): racy. Key must include the run, else the user gets only one email ever. |
| **Status state machine** `PENDING → SENT` + reaper | **Used here.** Handles the crash gap. |
| **Idempotency key to the provider** | **Used here.** Only thing that closes the send-vs-record gap. Many providers support it (SES/SendGrid/Stripe-style); otherwise dedupe on your own message-id if they don't. |
| **Optimistic lock** (`@Version`) | Works for claiming, but loser gets an exception and retries; more contention than a conditional `UPDATE` |
| **Fencing token** | Needed only if the downstream resource can reject stale writers (DB, storage). Not email. |

### C. Distribute the 1000 jobs across workers
| Option | Notes |
|---|---|
| **DB outbox + `FOR UPDATE SKIP LOCKED`** (Postgres/MySQL 8) | **Used here.** No new infra; workers skip rows other workers hold → no contention. Row locks are held only for the claim statement, not during the send. |
| **DB outbox + conditional `UPDATE`** (portable, no SKIP LOCKED) | Works anywhere (first version of this demo on H2), but workers fight over the same rows. |
| **Message queue** (Kafka keyed by userId / SQS / RabbitMQ) | Best for scale and backpressure. At-least-once delivery → consumer still needs idempotency (see `../../duplicate-delivery-demo`). Natural next step: publisher produces to Kafka, workers = consumer group; not done here to keep the outbox visible. |
| **Partition by user range** (instance k handles `userId % N == k`) | No coordination needed but breaks on scale up/down; no failover |
| **Scheduler-per-user** (delayed jobs, e.g. JobRunr/db-scheduler) | Libraries doing claim + retry + heartbeat for you; good production shortcut |

### D. Rejected / why
- **"Just check `emails_sent` first"**: two instances both see "not sent" and both send. Use the unique-constraint insert as the claim.
- **Redis lock alone**: correctness depends on timing (GC pause), which can't be bounded.
- **Send in the same DB transaction as the claim**: holds locks during network I/O and still can't make the email atomic with commit.

## Recommendation
Postgres + ShedLock for the publisher, outbox with `UNIQUE(run_id,user_id)` + `SKIP LOCKED` workers (or Kafka
if volume/backpressure matters), provider idempotency key, reaper. That is what this demo does.

## Notes / limits
- Mock provider's tables live in the same DB for convenience (a real provider is external, and its dedup window
  is finite: check how long your provider remembers idempotency keys vs. your max retry delay).
- Two crash modes are exercised: simulated (skip `markSent` after a successful send, to hit the dual-write gap
  deterministically) and a real `kill -9` of one JVM in `run-instances.sh`.
- If the `kill -9`'d instance held the ShedLock reaper lock, recovery waits for `lockAtMostFor` (30s) to expire.
  That is `lockAtMostFor` doing its job.
- `claim-timeout-ms` must exceed the worst-case send time, or a slow (not dead) worker gets its row re-queued and
  the send repeats; the provider idempotency key and `markSent` ownership check keep that harmless.
- ShedLock 6.6.0 works with Spring Boot 4.1.1 / Spring 7 here.
