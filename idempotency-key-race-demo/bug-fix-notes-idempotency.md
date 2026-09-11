# Bug: check-then-act idempotency key allows double-charging

## The bug

`InMemoryIdempotencyStore` (`src/main/java/.../buggy/InMemoryIdempotencyStore.java`)
splits the idempotency check into two separate calls:

```java
public boolean hasBeenSeen(String idempotencyKey) { return seenKeys.contains(idempotencyKey); }
public void markSeen(String idempotencyKey) { seenKeys.add(idempotencyKey); }
```

`BuggyPaymentController` uses them as check-then-act:

```java
if (!idempotencyStore.hasBeenSeen(idempotencyKey)) {
    chargeSimulator.charge(idempotencyKey);   // simulates the gateway call
    idempotencyStore.markSeen(idempotencyKey);
    return processed = true;
}
return processed = false; // "duplicate"
```

The backing `Set` is thread-safe on its own (`Collections.synchronizedSet`),
but that only protects each individual call — nothing makes the **sequence**
of "check" then "charge" then "mark" atomic. When a client times out and
retries with the same idempotency key, two requests can both call
`hasBeenSeen()` and get `false` before either one reaches `markSeen()`. Both
proceed to charge the card.

This is a textbook TOCTOU (time-of-check to time-of-use) race — the same
shape of bug as [the singleton-field race](../singleton-scope-bug-demo/bug-fix-notes.md),
but here the race window is between a read and a write to a store, not
between two threads touching the same object field.

## Reproduction

`RaceConditionReproductionTest` fires 50 concurrent retries of the **same**
idempotency key — using a `CountDownLatch` so all 50 threads hit the
endpoint at the same instant — and checks how many times `ChargeSimulator`
actually recorded a charge for that key.

- `givenCheckThenActIdempotencyCheck_whenSameKeyRetriedConcurrently_thenCustomerChargedMultipleTimes` — **charge count > 1**, confirmed.
- Same test against both fixed endpoints below — **charge count == 1**, every run.

## The fix — make "claim this key" a single atomic operation

The read and the write have to become one operation that only one caller
can win. Two ways to get that atomicity, both implemented here:

### 1. Database unique constraint (`fixed/db/`)

Give the idempotency key column a unique constraint, and treat the *insert
itself* as the claim — not a prior `SELECT`:

```java
@Column(name = "idempotency_key", nullable = false, unique = true)
private String idempotencyKey;
```

```java
@Transactional
public void insert(String idempotencyKey) {
    repository.saveAndFlush(new IdempotencyRecord(idempotencyKey));
}
```

Two concurrent requests can both attempt the insert, but the database
guarantees only one commits — the second always fails with a constraint
violation (`DataIntegrityViolationException` in Spring), which the caller
treats as "already claimed, this is a duplicate."

**Gotcha worth knowing**: don't catch that exception inside the same
`@Transactional` method that did the insert. Once Hibernate's flush fails
on a constraint violation, it marks that transaction rollback-only at the
ORM level — catching the exception doesn't change that, and the method
still fails on commit with `UnexpectedRollbackException`. The fix here
splits it into two beans: `IdempotencyInsertService.insert()` (its own
`@Transactional` boundary, lets the exception propagate — a clean, expected
rollback of *that* transaction) and `DbClaimService.tryClaim()` (no
transaction of its own, calls `insert()` through the Spring proxy and
catches the exception there).

### 2. Redis `SET ... NX` (`fixed/redis/`)

```java
Boolean claimed = redisTemplate.opsForValue()
        .setIfAbsent("idempotency:" + idempotencyKey, "PROCESSED", Duration.ofMinutes(10));
return Boolean.TRUE.equals(claimed);
```

`SETNX` (via `setIfAbsent`) is a single atomic command on the Redis server.
Two concurrent claims both reach Redis, but Redis processes commands
one at a time — only the first `SETNX` returns `true`; every other
concurrent (or later) call for the same key returns `false` until the key
expires or is deleted. No transaction, no constraint, no gotcha — the
atomicity is a property of the single command.

## Which one to use

Both close the race completely — pick based on what's already in your
stack:

- **DB unique constraint**: no new infrastructure if you already have a
  relational store; the idempotency record can live next to the payment
  row in the same transaction as the rest of the write. Slower than Redis,
  and constraint violations show up as (expected, but noisy) errors in DB
  logs.
- **Redis `SETNX`**: faster, and a TTL gives you automatic cleanup of old
  keys for free. Requires Redis to be available and adds it as a
  dependency for a correctness-critical path — if Redis is down, claims
  can't happen at all (fail closed, don't silently skip the check).

## Rule of thumb

A separate "check" and "act" is never atomic by itself, no matter how
thread-safe each individual step is. Idempotency (and any other
claim-a-resource-once problem — rate limiting, distributed locks, unique
job dispatch) needs a single atomic check-and-set: a unique constraint the
database enforces, or an atomic command like Redis `SETNX`/`SET NX`.
