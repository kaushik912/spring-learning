# Bug: consumer lag climbs even though consumers report healthy

## The bug

`BuggyOrderListener` (`src/main/java/.../buggy/BuggyOrderListener.java`) is a
single consumer thread doing a slow per-message call:

```java
@KafkaListener(topics = OrderTopics.BUGGY_ORDERS, groupId = OrderTopics.BUGGY_GROUP, concurrency = "1")
public void onMessage(String payload) throws InterruptedException {
    Thread.sleep(30); // stands in for a slow downstream call
}
```

It never throws, never crashes, never restarts. Every health check says
it's up. But it can only process about 33 messages/second, and
`OrderProducer` sustains about 66 messages/second onto its topic. That's
the entire bug: **consume rate < produce rate**. No error to catch, no
exception to log — the consumer is doing exactly what it's supposed to do,
just too slowly.

## What "lag" actually is

Lag isn't a health signal — it's `produce rate − consume rate`, accumulated
over time. `ConsumerLagService` computes it the same way real monitoring
tools do:

```java
long lag = logEndOffset(topic, partition) - committedOffset(groupId, topic, partition);
```

summed across partitions. That number has no idea whether the consumer is
"healthy." A perfectly stable consumer that's merely too slow (or there
just aren't enough consumer instances/partitions) makes this number grow
just as reliably as a crashed one — the difference is a crashed consumer's
lag jumps once and then plateaus, while a too-slow consumer's lag keeps
climbing for as long as the rate mismatch holds.

## Reproduction

`ConsumerLagReproductionTest` sustains ~66 msgs/sec of load against each
scenario's topic for ~2.25 seconds and samples real lag via
`GET /api/lag/{groupId}/{topic}`:

- `givenSlowSingleThreadedConsumer_whenLoadIsSustained_thenLagClimbsDespiteNoErrors`
  — lag measured partway through production is *lower* than lag measured
  at the end. It's still climbing while messages keep arriving, exactly as
  the prompt describes, and no exception is ever thrown anywhere.

Also verified against real dockerized Kafka (`docker compose up -d`):
POST `/api/load/buggy-orders?count=150&intervalMillis=15`, then sampling
`/api/lag/buggy-order-processor/buggy-orders` at t=1s and t=3s showed lag
climbing from **44 → 73** — a live, continuously growing backlog with a
consumer that never once errors.

## The fix — there are exactly two levers

Lag is a rate problem, so the fix is always one of two things: make the
consumer faster, or add more consumers. Both are implemented here for the
same slow-call scenario:

### 1. Speed up per-message processing (`fixed/fast/FastOrderListener`)

```java
@KafkaListener(topics = OrderTopics.FAST_ORDERS, groupId = OrderTopics.FAST_GROUP, concurrency = "1")
public void onMessage(String payload) throws InterruptedException {
    Thread.sleep(3); // the downstream call got faster
}
```

Same single thread, same topic shape — just less work per message (a
non-blocking client instead of a blocking one, batching several downstream
calls into one, caching a lookup that used to hit the network every time,
or simply cutting unnecessary work). Consume rate now comfortably clears
the produce rate, so lag never has room to build. Reproduction: lag sampled
right after a full production run stays effectively at 0.

### 2. Add consumer parallelism (`fixed/scaled/ScaledOrderListener`)

```java
@KafkaListener(topics = OrderTopics.SCALED_ORDERS, groupId = OrderTopics.SCALED_GROUP, concurrency = "3")
public void onMessage(String payload) throws InterruptedException {
    Thread.sleep(30); // exactly as slow as the buggy listener
}
```

Same per-message cost as the buggy listener — sometimes the downstream
call genuinely can't be made faster. `concurrency = "3"` gives this
listener 3 consumer threads (each its own `Consumer` client, functionally
equivalent to running 3 separate consumer instances in the group), matched
to the topic's 3 partitions. No thread got faster, but aggregate throughput
tripled, which is enough to outrun the produce rate. Reproduction: lag
also stays near 0.

**This only works up to the partition count.** A consumer group can never
have more *active* consumers than partitions — extra threads/instances
beyond the partition count sit idle. If a topic has 3 partitions, scaling
past `concurrency = "3"` (or a 4th instance in the group) buys nothing;
the partition count has to grow too.

## Rule of thumb

"No crashes, no restarts, no errors in the logs" is not the same claim as
"keeping up." A consumer can be perfectly healthy by every liveness check
and still be the slowest thing in the pipeline. When lag is climbing,
don't go looking for an exception — measure per-message processing time,
check whether the consumer is blocked on a slow downstream call, and check
whether partition count and consumer count are large enough for the
produce rate. The fix is always speed (make each message cheaper) or scale
(add consumers up to the partition count, and add partitions if you're
already at that ceiling) — never "the consumer says it's fine."
