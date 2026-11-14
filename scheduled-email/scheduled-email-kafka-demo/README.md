# Scheduled email on multiple instances: the Kafka version

A `@Scheduled` job emails 1000 users, the app runs as 2+ instances, and each user must get **one** email.
Here Kafka is the work queue: one publisher produces a message per user, all instances consume them.

Sibling projects (same problem): [`../scheduled-email-simple-jdbc-demo`](../scheduled-email-simple-jdbc-demo)
(status table + sweeper, no Kafka) and [`../scheduled-email-dedup-demo`](../scheduled-email-dedup-demo)
(DB outbox + `SKIP LOCKED` workers + reaper, the heavy hand-rolled queue).

## The idea

```
@Scheduled publisher on every instance, ShedLock lets ONE run per tick
   └─ produce 1000 messages to topic `email-requests`   key = userId, value = "runId,userId,resendCount"
                    │   6 partitions
   consumer group `email-senders`   (instance 1 + instance 2, 2 threads each)
                    │
   per message:  already SENT?  ──yes──▶ skip
                 send email (idempotency key runId:userId:rN)
                 record SENT in Postgres
                 listener returns ──▶ Spring Kafka commits the offset
                    │ listener throws (provider 503, crash...)
   retry 2x, 500 ms apart ──still failing──▶ topic `email-requests-dlt` ──▶ recorded as DEAD
```

### What Kafka replaced from the heavy demo

| Heavy demo (hand-rolled) | Kafka version |
|---|---|
| `email_outbox` table + `SKIP LOCKED` claim | Partitions: each message goes to exactly one consumer in the group |
| Reaper for crashed workers | Rebalance: a dead consumer's uncommitted messages are redelivered to another instance |
| `attempts` column + retry loop | `DefaultErrorHandler` retries, then the dead-letter topic |
| "dead" row (attempts == max) | A message in `email-requests-dlt`, recorded as `DEAD` |
| `resend` endpoint | Publish a new message with `resendCount + 1` |

### Postgres still holds a little state (`email_results`, `email_runs`)
- `email_results (run, user, resend_count, status SENT|DEAD)`: the dedup memory ("was this already sent?"), also the
  source for the report. Without it nothing would survive a restart.
- `email_runs`: "this run was fully published" marker, written *after* all messages are acked. A publisher that
  crashes halfway leaves no marker, so the next tick publishes again, and the consumers dedupe.

## Why this design is safe

| Situation | What happens |
|---|---|
| Two publishers (ShedLock expired, manual publish) | Users get two messages. Same key goes to the same partition, so the second is handled after the first and skipped (`alreadySent`) |
| Provider 503 | Listener throws, retried twice, then dead-lettered |
| Consumer dies after the provider accepted, before we record it | Message redelivered, resend hits the provider with the same idempotency key, provider ignores it |
| An instance is `kill -9`'d | After the session timeout Kafka reassigns its partitions; uncommitted messages are redelivered |
| Re-publishing a run | Users already SENT are skipped; users still dead-lettered get a fresh set of attempts |
| Customer complains | `resend`: new message, `resendCount + 1`, so a new idempotency key (the provider would drop the old one) |
| Stale original request arrives after a resend was sent | `alreadySent` also counts *newer* resends as "sent", so the old request is skipped |

Kafka's exactly-once transactions are **not** used on purpose: the email is an external side effect that cannot join a
Kafka transaction. Idempotency (dedup check + provider idempotency key) is what actually protects the user.

## How to run

Needs Docker (Kafka KRaft + Postgres 17) and Java 21.

```bash
./mvnw test              # Testcontainers Kafka + Postgres, 4 tests
./run-instances.sh       # compose Kafka+Postgres, 2 JVMs (:8095, :8096), kill -9 one, resend, re-publish
docker compose down -v   # cleanup
```

By hand:
```bash
docker compose up -d --wait
./mvnw spring-boot:run                                        # :8095, publishes on a 20s cron
curl localhost:8095/admin/emails/report                       # sent/dead counts, per-instance split, provider stats
curl -X POST localhost:8095/admin/emails/2026-11-14/publish   # publish the run again (duplicates are skipped)
curl -X POST localhost:8095/admin/emails/2026-11-14/177/resend
```

Ports: Kafka 9094, Postgres 5436, apps 8095/8096 (so it runs next to the other demos). Knobs in
`application.properties` (`demo.*`): `user-count`, `provider-failure-rate`, `crash-after-send-rate`, `retries`,
`retry-backoff-ms`, `publish-cron`, `provider-latency-ms`.

## What the tests / runs showed

- **Test, duplicate publishes + failures** (two publishers = 2000 messages, 30% provider 503s, 3% crash-after-send):
  every user ended SENT or DEAD, 999 sent, 1 dead, ~974 duplicate messages skipped, ~42 duplicate sends absorbed by
  the provider's idempotency key, no user delivered twice.
- **Test, resend:** a SENT user gets a second delivery under a new key (`...:r1`).
- **Test, dead-lettered user:** provider "broken": all 3 go dead. After the "fix", `resend` delivers only that user.
- **Test, stale request:** after a resend, re-publishing the run does not email that user a third time.
- **Real run (`run-instances.sh`), 2 JVMs:**
  1. One instance `kill -9`'d after ~170 users were sent. The survivor took over its partitions and finished:
     **996 SENT, 4 dead-lettered**, 260 provider 503s absorbed by retries, 29 duplicate sends ignored by the provider.
     The killed instance had sent 89 before dying; the survivor sent the other 907.
  2. `resend` for dead user 438: new key `2026-11-14:438:r1`, delivered (997 sent, 3 dead).
  3. The whole run re-published (1000 duplicate messages): already-sent users skipped, the 3 still-dead users got
     a fresh set of attempts and succeeded. Final: **1000 SENT, 1000 provider deliveries, 0 dead**, so exactly one
     delivery per user, and user 438's old `r0` request was skipped because `r1` had already been sent.

## Things worth knowing

- **Listener return = commit.** The default ack mode commits the offset only after the listener returned normally
  (or the record was dead-lettered), so "ack after the DB write" needs no manual ack code. A poll can hold many records,
  so a crash can redeliver a whole batch: that is why every step is idempotent.
- **Retries block the partition.** `FixedBackOff` retries in place, so a failing message pauses its partition for
  ~1s (2 x 500 ms). Fine here. The production-style alternative is non-blocking retry topics (`@RetryableTopic`).
- **Parallelism is capped by partitions** (6): more than 6 consumer threads in the group would sit idle.
- **`session.timeout.ms=10000` is a demo setting** so a killed instance is noticed in 10s (default is 45s).
- **Message format is a plain string** (`runId,userId,resendCount`) to keep serializer config out of the way.
- **No auth on the admin endpoints.** Add it before real use.
- **Dead-letter bookkeeping** is a second listener (`email-dead-letters` group) that writes `DEAD` rows. If a duplicate
  of the same request succeeds later, `markSent` removes the dead rows for that user.

## Simple vs heavy vs Kafka

| | Simple (status table + sweeper) | Heavy (outbox + workers) | Kafka (this) |
|---|---|---|---|
| Spread work across instances | No (one runner) | Yes, `SKIP LOCKED` workers | Yes, partitions |
| Crash recovery | Next tick re-sends PENDING | Reaper re-queues stale CLAIMED | Rebalance redelivers |
| Retries / give up | Sweeper + attempts | attempts + reaper | Error handler + dead-letter topic |
| Extra infrastructure | None | None | Kafka |
| Hand-written queue logic | Little | A lot (claim, reaper) | None, the broker does it |
| Replay / backpressure / fan-out | No | Limited | Built in |

**Interview framing:** ShedLock for one publisher, one message per user keyed by userId, consumer group for scale,
at-least-once delivery made safe by a dedup check and a provider idempotency key, retries then a dead-letter topic,
and a resend that bumps the idempotency key. Kafka transactions are not the answer here because email is not
transactional.
