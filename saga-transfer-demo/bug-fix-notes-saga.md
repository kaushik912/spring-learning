# Bug: no distributed transaction across two services' databases

## Setup

`SenderAccountStore` and `ReceiverAccountStore` each stand in for a separate
service's own database — in this demo they're two separate in-memory stores
in one process, but the property that matters is real: **nothing ties them
into a single transaction**, exactly like a real sender-service and
receiver-service each with their own database.

## The bug

`BuggyTransferController` (`src/main/java/.../buggy/`) treats the transfer
as if it were one atomic step:

```java
boolean debited = senderAccountStore.debit(request.fromAccountId(), request.amount());
if (!debited) { return Map.of("status", "FAILED", ...); }

// <-- simulated crash happens here -->

receiverAccountStore.credit(request.toAccountId(), request.amount());
```

If both accounts lived in the same database, wrapping this in one
`@Transactional` method would make a crash between the two lines roll
everything back — the debit would never be visible. But the debit already
committed in the sender's own local transaction the moment it ran. There is
no transaction spanning both stores, so a crash between the two lines
leaves the money nowhere: gone from the sender, never arrived at the
receiver.

## Reproduction

`TransferSagaReproductionTest.givenNoSharedTransaction_whenCrashHappensAfterDebitBeforeCredit_thenMoneyIsStuck`
calls the buggy endpoint with `simulateCrash: true` and confirms: the sender
is debited, the receiver never gets credited, and there's no automatic way
back. Verified two ways:

- `./mvnw test` — automated, using an embedded Kafka broker (no Docker
  needed).
- Manually against real dockerized Kafka: `docker compose up -d`, then
  `POST /api/buggy/transfers {"fromAccountId":"alice","toAccountId":"bob","amount":30.00,"simulateCrash":true}`
  → alice's balance drops from 100 to 70, bob's stays at 50. The 30 is gone.

## The fix — a Saga: local transactions + explicit compensation

You can't hold one ACID transaction across two databases (or two services).
The Saga pattern accepts that and makes it explicit: each step is its own
local transaction, and if a later step fails, a **compensating action**
undoes the earlier one. You trade strict, immediate atomicity for eventual
consistency — but with a defined recovery path for every failure, instead
of "it can't happen" (`saga/` package, `docker compose up -d` if you want
to run it against real Kafka rather than the test's embedded broker).

This demo implements **orchestration**-style Saga: `SagaOrchestrator` is a
single component that tells each service what to do next and reacts to the
result, over Kafka:

```
Orchestrator          Sender-service           Receiver-service
    |--- debit-requested ---->|                        |
    |<-- debit-result --------|                        |
    |--- credit-requested ------------------------------>|
    |<-- credit-result -----------------------------------|
```

- **Happy path**: `debit-requested` → sender debits (`DEBITED`) →
  `credit-requested` → receiver credits (`COMPLETED`). Both stores end up
  consistent.
- **Failure path**: same start, but the credit step fails
  (`credit-requested` → receiver can't credit → `credit-result: false`).
  The orchestrator's `onCreditResult` then publishes `refund-requested` —
  the **compensating transaction** — and the sender credits the money back
  (`COMPENSATED`). The sender ends up exactly where they started; nothing
  is lost, nothing is duplicated.

The key design shift from the buggy version: **the debit has to actually be
reversible**. `SenderAccountStore.refund()` exists specifically so a failed
downstream step has something to call — you can't compensate a step that
has no undo.

```java
@KafkaListener(topics = SagaTopics.CREDIT_RESULT, groupId = "saga-orchestrator")
public void onCreditResult(StepResult result) {
    sagas.computeIfPresent(result.sagaId(), (id, state) -> {
        if (result.success()) {
            return state.withStatus(SagaStatus.COMPLETED, null);
        }
        kafkaTemplate.send(SagaTopics.REFUND_REQUESTED, id,
                new TransferCommand(id, state.fromAccountId(), state.amount()));
        return state.withStatus(SagaStatus.COMPENSATING, result.reason());
    });
}
```

### Reproduction of the fix

`TransferSagaReproductionTest` has two saga tests, both polling
`GET /api/saga/transfers/{sagaId}` until the saga reaches a terminal state
(Kafka processing is asynchronous, so there's no synchronous response to
assert on directly):

- `givenSaga_whenTransferSucceeds_thenBothSidesEndUpConsistent` — ends
  `COMPLETED`, sender debited, receiver credited.
- `givenSaga_whenCreditStepFails_thenDebitIsCompensatedAndSenderEndsUpWhole`
  — ends `COMPENSATED`, sender back to their starting balance, receiver
  untouched.

Also verified manually against real dockerized Kafka (`docker compose up -d`):
the same two scenarios via `curl`, watching `/api/accounts/sender/{id}` and
`/api/accounts/receiver/{id}` settle to the correct final balances.

## What this demo simplifies (and why it still holds)

- **One process, not three services.** Sender-service, receiver-service,
  and the orchestrator are packages in one Spring Boot app here, not
  separate deployables. What actually matters for the lesson — no shared
  transaction, communication only through Kafka messages and each side's
  own local commit — is real regardless of whether that boundary is a
  package or a network call.
- **In-memory stores, not real databases.** `ConcurrentHashMap` stands in
  for "the sender-service's database" / "the receiver-service's database."
  Swapping either for Postgres/MySQL changes nothing about the bug or the
  fix — the debit and the credit would still be two separate local
  transactions in two separate databases.
- **`simulateFailure` on the credit command** is a fault-injection hook
  added purely so tests can deterministically force the compensation path.
  A real receiver-service doesn't take instructions from the caller on
  whether to fail — but it can fail for its own reasons (validation, its
  DB being down, a timeout), and the orchestrator reacts identically either
  way: it only sees `credit-result: false` and doesn't need to know why.

## Rule of thumb

Once state lives in two different databases, no operation touching both is
atomic by construction — a crash, restart, or network partition between
the two writes is always possible, and "just wrap it in a transaction"
stops being an option. A Saga makes that reality explicit: break the
work into local transactions, and for every step that can happen *after*
one that already committed, design a compensating action that undoes it.
If a step doesn't have a sane compensation, that's a sign the step
boundaries are wrong, not that you can skip writing one.
