# Saga Transfer Demo

A small Spring Boot app that demonstrates the **Saga pattern** by first
showing the bug it fixes: money transfers across two services that each
have their own database, with no way to wrap both in a single transaction.

## The problem in one sentence

`SenderAccountStore` and `ReceiverAccountStore` stand in for two separate
services, each with its own database. There is no way to put a debit in one
and a credit in the other inside a single ACID transaction — so if something
fails in between, you can end up with money gone from the sender and never
credited to the receiver.

This repo has two implementations side by side so you can see both the bug
and the fix:

| Package  | What it shows                                                   |
|----------|------------------------------------------------------------------|
| `buggy/` | Debit-then-credit treated as one atomic step. It isn't — a crash between the two leaves money stuck. |
| `saga/`  | The fix: each step is its own local transaction, coordinated over Kafka, with an explicit compensating action (refund) if a later step fails. |

See [`bug-fix-notes-saga.md`](./bug-fix-notes-saga.md) for a detailed
walkthrough of the bug, the fix, and the reasoning behind it.

## How the saga works

`SagaOrchestrator` drives the transfer as a sequence of steps over Kafka
topics, reacting to the result of each one:

```
Orchestrator          Sender-service           Receiver-service
    |--- debit-requested ---->|                        |
    |<-- debit-result --------|                        |
    |--- credit-requested ------------------------------>|
    |<-- credit-result -----------------------------------|
```

- **Happy path**: debit succeeds → credit succeeds → saga status
  `COMPLETED`.
- **Failure path**: debit succeeds, but the credit step fails → the
  orchestrator sends a `refund-requested` command (the **compensating
  action**) → the sender is refunded → saga status `COMPENSATED`. The
  sender ends up exactly where they started.

A saga moves through these statuses (see `SagaStatus`): `STARTED` →
`DEBITED` → `COMPLETED`, or `STARTED` → `DEBITED` → `COMPENSATING` →
`COMPENSATED` (or `FAILED` if the debit itself fails, e.g. insufficient
funds).

## Running it

### Option A: run the tests (no Docker needed)

The tests spin up an embedded Kafka broker automatically:

```bash
./mvnw test
```

`TransferSagaReproductionTest` reproduces both the bug (money stuck after a
simulated crash) and the fix (happy path + compensation path).

### Option B: run it live against real Kafka

1. Start Kafka:
   ```bash
   docker compose up -d
   ```
2. Start the app:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Explore the API via Swagger UI at
   [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html),
   or use `curl`/Postman as below.

There's one seeded account: `alice` starts with a balance of `100.00` in
the sender store. `bob` starts at `50.00` in the receiver store.

## Trying it out with curl

**See the bug** — transfer with a simulated crash between debit and credit:

```bash
curl -X POST http://localhost:8080/api/buggy/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"alice","toAccountId":"bob","amount":30.00,"simulateCrash":true}'
```

Check balances — alice is debited, bob never gets credited, the 30 is gone:

```bash
curl http://localhost:8080/api/accounts/sender/alice
curl http://localhost:8080/api/accounts/receiver/bob
```

**See the fix** — happy path:

```bash
curl -X POST http://localhost:8080/api/saga/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"alice","toAccountId":"bob","amount":30.00,"simulateCreditFailure":false}'
```

This returns a `sagaId`. Poll its status until it reaches `COMPLETED`:

```bash
curl http://localhost:8080/api/saga/transfers/{sagaId}
```

**See the fix's compensation path** — force the credit step to fail:

```bash
curl -X POST http://localhost:8080/api/saga/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"alice","toAccountId":"bob","amount":30.00,"simulateCreditFailure":true}'
```

Poll the same status endpoint — the saga moves through `COMPENSATING` to
`COMPENSATED`, and alice's balance ends up unchanged.

## Project layout

```
saga/                    the fix: SagaOrchestrator, Kafka listeners for each
                          step (sender + receiver), saga state machine
buggy/                    the bug: single controller doing debit then credit
accounts/sender/          SenderAccountStore — stands in for the sender's DB
accounts/receiver/        ReceiverAccountStore — stands in for the receiver's DB
```

## Further reading

[`bug-fix-notes-saga.md`](./bug-fix-notes-saga.md) goes into more depth:
why the bug is real even though this demo runs in one process, what the
demo intentionally simplifies (one process instead of three deployables,
in-memory stores instead of real databases), and the general rule of thumb
for when you need a saga.
