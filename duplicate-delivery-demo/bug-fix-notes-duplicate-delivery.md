# Bug: Kafka redelivers the same event, consumer processes it twice

## The bug

`BuggyEventListener` (`src/main/java/.../buggy/BuggyEventListener.java`)
does the side effect first and has no memory of what it's already handled:

```java
@KafkaListener(topics = EventTopics.BUGGY_EVENTS, groupId = EventTopics.BUGGY_GROUP)
public void onMessage(String eventId) {
    emailService.sendConfirmation(eventId);
    throw new RuntimeException("simulated downstream failure after side effect for event " + eventId);
}
```

Kafka only guarantees **at-least-once** delivery per consumer group — never
exactly-once at the transport layer. Whenever the consumer fails to record
"I finished this record" (offset commit) after doing the work, Kafka's only
safe option is to hand the record out again once the consumer is available.
That can happen for several real reasons, all with the same effect:

- the listener throws after the side effect but before the container
  acknowledges/commits (what this demo simulates, via
  `KafkaRetryConfig`'s `DefaultErrorHandler` + `FixedBackOff(0, 1)` — one
  guaranteed redelivery instead of a flaky one)
- the process crashes or is killed between processing and committing
- a consumer group rebalance reassigns the partition before the commit
  lands, and the new owner starts from the last *committed* offset — not
  the last *processed* one

None of these are Kafka malfunctioning — they're at-least-once working as
designed. A client (or consumer) not built to expect duplicates will
observe the same event's side effect run more than once.

## Reproduction

`DuplicateDeliveryReproductionTest` publishes one event, lets the
container's error handler redeliver it (the throw above guarantees
exactly one redelivery — deterministic, no need to crash a real consumer
or force a rebalance to prove the point), and checks how many times
`EmailService` actually ran for that `eventId`:

- `givenNonIdempotentListener_whenKafkaRedeliversSameRecord_thenSideEffectRunsTwice` — **count == 2**, confirmed.
- `givenIdempotentListener_whenKafkaRedeliversSameRecord_thenSideEffectRunsOnce` — same redelivery happens, **count == 1**.

## The fix — dedup on the consumer side

`FixedEventListener` (`src/main/java/.../fixed/FixedEventListener.java`)
tracks which event IDs it has already handled and skips the side effect
for anything seen before:

```java
private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

@KafkaListener(topics = EventTopics.FIXED_EVENTS, groupId = EventTopics.FIXED_GROUP)
public void onMessage(String eventId) {
    if (!processedEventIds.add(eventId)) {
        return; // already handled this exact event - skip silently
    }
    emailService.sendConfirmation(eventId);
    throw new RuntimeException("simulated downstream failure after side effect for event " + eventId);
}
```

The listener still gets invoked twice — nothing here changes Kafka's
delivery guarantee, and nothing should try to. What changes is that the
second invocation is a no-op: `Set.add()` returns `false` for an ID
already present, so the side effect never runs again.

The dedup key doesn't have to be a separate "idempotency key" field —
often, as here, the event's own natural identity (an event ID, an order
ID, a (aggregate ID, version) pair) is enough. Use whatever uniquely
identifies "this exact business event," not a value that changes on
redelivery.

### Where this in-memory Set falls short

A `ConcurrentHashMap`-backed `Set` only dedups within one running JVM. It's
enough here because a single-partition topic with one consumer never has
two *concurrent* deliveries of the same record — the second delivery only
happens after the first one fails and is retried by the same running
container. It is **not** enough once:

- the consumer process restarts (the set is empty again, and Kafka may
  still redeliver records from before the restart)
- there's more than one consumer instance in the group (each has its own
  set)

For those cases the dedup store has to be external and shared — a DB
table with a unique constraint on the event ID, or Redis `SETNX` — exactly
the same atomic-claim shape covered in
[idempotency-key-race-demo/bug-fix-notes-idempotency.md](../idempotency-key-race-demo/bug-fix-notes-idempotency.md).
The difference between that demo and this one: that one is about a race
between *concurrent* requests racing a check-then-act; this one is about a
*single* consumer seeing the *same* record more than once, sequentially.
Both need an atomic "have I claimed this before?" — just for different
reasons.

## Making the dedup store survive a restart

`PersistentEventListener` (`src/main/java/.../fixed/persistent/`) swaps
the in-memory `Set` for a database row, via `ProcessedEventStore`:

```java
public boolean tryClaim(String eventId) {
    if (repository.existsByEventId(eventId)) {
        return false;
    }
    repository.save(new ProcessedEvent(eventId));
    return true;
}
```

`DuplicateDeliveryReproductionTest`'s
`givenEventAlreadyClaimedInStore_whenFreshStoreInstanceChecksIt_thenClaimIsRejected`
proves the point: it claims an event through the normal Spring-managed
`ProcessedEventStore`, then constructs a **second, brand-new**
`ProcessedEventStore` instance by hand — sharing no JVM state whatsoever
with the first — and shows that instance still rejects the same claim. If
the state lived in a field like `FixedEventListener`'s `Set`, the fresh
instance would have no way to know. That's as close as a same-JVM test can
get to proving restart-survival without literally killing the process; see
the `data/` directory produced by a real run for the actual disk-backed
proof (below).

### Two gotchas worth knowing

**`@Id` shape matters.** `ProcessedEvent` uses an auto-generated `Long id`,
with `eventId` as a separate `@Column(unique = true)` — not `eventId`
itself as `@Id`. With a manually-assigned `@Id`, Spring Data JPA's
`save()` can't tell "this is new" from "this already exists" purely from
the ID being non-null, so it calls `merge()` — a silent upsert. A
redelivered event would just update the existing row instead of being
rejected, quietly defeating the whole point. Making `eventId` a plain
unique column instead keeps every `save()` a fresh `persist()`, so the
unique constraint actually has something to reject.

**H2 mode matters.** The first draft of this fix used
`jdbc:h2:mem:...` (H2 in-memory mode). That does **not** survive a
restart — `DB_CLOSE_DELAY=-1` only keeps the in-memory database alive
across connection churn *within* the same running JVM; killing the JVM
destroys it exactly like the in-memory `Set` does. The main app config
uses `jdbc:h2:file:./data/duplicatedelivery` instead — an on-disk file
that's still there after `Ctrl+C` and a restart. Tests use a separate
`jdbc:h2:mem:duplicatedelivery-test` (fast, isolated, no files left
behind) — restart-survival there is proved by the fresh-instance test
above, not by relying on the test database surviving anything.

### Why check-then-insert is enough here (and when it wouldn't be)

`ProcessedEventStore.tryClaim` is a plain check-then-insert
(`existsByEventId` then `save`), not the atomic insert-and-catch-exception
split `idempotency-key-race-demo` needs (`IdempotencyInsertService` +
`DbClaimService`, kept in separate `@Transactional` beans to dodge
Hibernate's rollback-only-after-constraint-violation gotcha). That split
exists there because concurrent HTTP retries can hit multiple app
instances *at the same time*, genuinely racing the check against the
insert. Here, Kafka only ever hands a given partition's record to the
*one* consumer that currently owns it — the redelivery this demo relies on
only happens after that same consumer's prior attempt has already failed,
sequentially, never concurrently with itself.

The unique constraint on `event_id` is still there as a backstop, not as
the enforced atomicity mechanism — worth being honest about, not glossing
over: a rebalance/zombie-consumer edge case (an old consumer slow to
notice it lost a partition while a new one has already picked it up) could
in theory let two consumer *processes* briefly overlap on the same record.
Both could pass `existsByEventId` before either `save()`s. Because
`eventId` isn't the `@Id` (see above), `save()` on the loser is still a
`persist()`, which the unique constraint would reject with a
`DataIntegrityViolationException` — but `tryClaim` doesn't catch that
here, so it propagates out of `onMessage` as an uncaught exception instead
of a clean "duplicate, skip" no-op. The side effect still doesn't run
twice (the exception unwinds before `emailService.sendConfirmation` is
reached), it just surfaces as a failed/retried delivery rather than a
graceful skip — noisier than it needs to be. A deployment that wants that
last bit cleaned up too should reach for the same split-transaction
insert-and-catch pattern as `idempotency-key-race-demo`
(`DataIntegrityViolationException` caught in a separate, uninvolved bean),
not this simpler version.

## Rule of thumb

At-least-once delivery is a deliberate trade-off, not a bug to fight at
the broker: a system that occasionally redelivers a message is safer than
one that can silently drop it, and Kafka (like SQS, RabbitMQ with certain
ack modes, etc.) picks safety. The correct response to "I saw a duplicate"
is never "make the broker stop doing that" — it's "make the consumer
idempotent": track what's already been processed (in-memory if a single
instance is provably enough, externally and atomically if not) and skip
duplicates by that key.
