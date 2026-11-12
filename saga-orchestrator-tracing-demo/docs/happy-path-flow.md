# Happy path flow (business view)

Order for SKU-100, qty 1, amount 100. Tracing left out; see `tracing-code-tutorial.md`.

## Sequence

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant O as Order service<br/>(Orchestrator + Engine)
    participant K as Kafka
    participant I as Inventory service
    participant P as Payment service

    U->>O: POST /orders (SKU-100, qty 1, amount 100)
    Note over O: begin(): insert saga STARTED,<br/>arm 5s timeout, commit
    O-->>U: 202 Accepted {orderId}
    O->>K: inventory-commands: RESERVE_INVENTORY (key=orderId)
    K->>I: consume command
    Note over I: idempotency check, then<br/>update inventory where available >= qty,<br/>insert reservation
    I->>K: order-replies: INVENTORY_RESERVED
    K->>O: ReplyListener -> onReply
    Note over O: applyReply (row lock):<br/>STARTED -> INVENTORY_RESERVED,<br/>step AUTHORIZE_PAYMENT, arm timeout
    O->>K: payment-commands: AUTHORIZE_PAYMENT
    K->>P: consume command
    Note over P: idempotency check, amount > 1000 fails,<br/>==777 sleeps 10s, else insert payment
    P->>K: order-replies: PAYMENT_AUTHORIZED
    K->>O: ReplyListener -> onReply
    Note over O: INVENTORY_RESERVED -> PAYMENT_AUTHORIZED -> COMPLETED,<br/>clear timer, return null (nothing to send)
```

Every Kafka send happens after the DB transaction commits.

## States (with unhappy paths)

```mermaid
stateDiagram-v2
    [*] --> STARTED
    STARTED --> INVENTORY_RESERVED: InventoryReserved
    INVENTORY_RESERVED --> PAYMENT_AUTHORIZED: PaymentAuthorized
    PAYMENT_AUTHORIZED --> COMPLETED
    COMPLETED --> [*]

    STARTED --> COMPENSATING: InventoryRejected / timeout
    INVENTORY_RESERVED --> COMPENSATING: PaymentFailed / timeout
    COMPENSATING --> COMPENSATED: compensations done
    COMPENSATED --> [*]
```

## Key points

- `SagaEngine` is the state machine: each method is one `@Transactional` unit and returns the next `Command` (or `null`).
- `SagaOrchestrator` sends that command after commit. `send(null)` is a no-op (terminal state, ignored duplicate/late reply, or nothing due).
- Command id = `orderId:STEP`, same on every retry. Participants use it for idempotency (`processed_command`).
- Inventory check is atomic: `update ... where available >= qty`. 0 rows = `INVENTORY_REJECTED`.
- Payment simulation: `amount > 1000` -> `PAYMENT_FAILED`; `amount == 777` sleeps 10s (> 5s step timeout) -> timeout -> compensation; late `PAYMENT_AUTHORIZED` is ignored.
- Each step arms `nextActionAt`; `scanDue` fires timeouts/retries from it.
- Terminal states: `COMPLETED`, `COMPENSATED`. Compensation retries give up after `max-compensation-attempts` (manual action).
