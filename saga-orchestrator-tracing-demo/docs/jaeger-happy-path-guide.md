# Guide: Read the Happy-Path Trace in Jaeger (and map it to code)

Goal: look at one trace (e.g. `e95709e41b76a6191bbd56d45f496a9a`) and explain every span by pointing to the line of code that made it.

## 0. Mental model (30 sec)

One order = one **trace**. A trace = tree of **spans**. Tree nests because trace context (`traceparent`) travels:
- HTTP -> code: OTel agent makes a server span.
- Code -> Kafka: agent injects `traceparent` into record headers (`<topic> publish`).
- Kafka -> code: agent reads header, makes `<topic> process` as child of the publish.

Two kinds of spans:

| Kind | Names | Made by |
|---|---|---|
| **Agent (automatic)** | `POST /orders`, `inventory-commands publish`, `... process`, `INSERT ...`, `UPDATE ...` | OTel Java agent, no code |
| **Custom (ours)** | `saga.start`, `saga.reply X`, `saga.transition A->B`, `inventory.handle X`, `payment.handle X` | `SagaTracing.start(...)` in code |

**Tip:** Custom spans carry the business meaning. Focus on them; the agent ones are plumbing.

## 1. Get a clean trace (optional, if yours is noisy)

```bash
order() { curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' -d "{\"sku\":\"$1\",\"qty\":1,\"amount\":$2}"; echo; }
order SKU-1 100
# -> {"orderId":"<ORDER_ID>"}
curl -s localhost:8080/orders/<ORDER_ID>/saga | jq
```

`/saga` output = ground truth: `state`, `traceId`, and `timeline` (each state change with reason).
Compare it with Jaeger: every timeline row should match one `saga.transition` span.

## 2. Open the trace and set up the UI

1. `http://localhost:16686/trace/e95709e41b76a6191bbd56d45f496a9a`
2. Top-right dropdown -> **Trace Timeline** (default). Collapse noise:
   - Top search box ("Search…") -> type `saga` or `handle`. Matching spans highlight; others dim.
   - Or use **Trace Graph / Flamegraph** views only after you understand the timeline.
3. Click a span -> expand **Tags**. Look for `order.id`, `saga.state`, `saga.step`, `idempotency.key`.
4. Left column of the timeline = service name colour. 3 colours = order / inventory / payment.

## 3. Walk the happy path span by span

Expected tree (README section c). Do it in this order. For each step: find the span, then open the code.

### Step 1 - `POST /orders` (service: order-service)
- Agent span. Root of trace. Duration ≈ whole HTTP call (short, returns 202 fast; saga continues async, which is why later spans start *after* it ends).
- Code: `order-service/.../OrderController.java:28` `create()` -> `orchestrator.start(req)`.

### Step 2 - `saga.start` (order)
- Tags: `saga.state=STARTED`, `saga.step=RESERVE_INVENTORY`.
- Code: `SagaOrchestrator.java:50` `SagaTracing.start("saga.start", ...)`, then `engine.begin(...)` (line 53).
- Inside it, look for child spans `INSERT orders.saga_instance` and `INSERT ... saga_step_log` (agent JDBC spans) = `repo.insert` + `repo.log` in `SagaEngine.java:62-63` (the DB transaction lives in `SagaEngine`, `@Transactional`).
- `SagaEngine.java:60-61` saves `traceId` + `traceParent` into the DB. That's how `/saga` gives you the trace id, and how timeouts rejoin the trace.

### Step 3 - `inventory-commands publish` (order)
- Agent span, child of `saga.start`. Made by `kafka.send(...)` at `SagaOrchestrator.java:118` (`send()`, called after `engine.begin` has committed, line 53, still inside the span).
- Topic comes from `CommandType.RESERVE_INVENTORY` -> `Topics.INVENTORY_COMMANDS`.
- **This is the hop where the trace crosses from order to Kafka.**

### Step 4 - `inventory-commands process` (inventory)
- Agent span, child of the publish. First span with a **different service** colour.
- Gap between publish end and process start = Kafka delivery latency.

### Step 5 - `inventory.handle RESERVE_INVENTORY` (inventory)
- Custom span. Tags: `saga.step=RESERVE_INVENTORY`, `idempotency.key=<orderId>:RESERVE_INVENTORY`.
- Code: `inventory-service/.../CommandListener.java:31`. Calls `service.handle(c)` (DB: stock update + `processed_command` insert; see the `UPDATE`/`INSERT inventory.*` child spans).
- Then line 35 `kafka.send(ORDER_REPLIES, reply)` -> child `order-replies publish`.

### Step 6 - `order-replies process` then `saga.reply INVENTORY_RESERVED` (order)
- Trace is back in order-service. Reply consumer: `ReplyListener.java:18` -> `SagaOrchestrator.onReply` (line 63) creates `saga.reply <TYPE>` (line 67), then calls `engine.applyReply` (line 69).
- Inside: `repo.lockByOrderId` = `SELECT ... FOR UPDATE` span (row lock), then `SagaEngine.applyReply` (line 70) switch on `INVENTORY_RESERVED`.

### Step 7 - `saga.transition STARTED->INVENTORY_RESERVED` (order)
- Code: `SagaEngine.transition()` at line 192. Child spans: `UPDATE saga_instance`, `INSERT saga_step_log`. Also emits the INFO log line.
- Then `sendStep(... AUTHORIZE_PAYMENT)` (`SagaEngine.java:81`) arms the 5s timeout (`nextActionAt`) and returns the next command.

### Step 8 - `payment-commands publish` -> `payment-commands process` -> `payment.handle AUTHORIZE_PAYMENT` -> `order-replies publish`
- Same pattern as steps 3-5, but payment-service. Code: `payment-service/.../CommandListener.java`.
- Amount 100 -> authorized. (>1000 fails, 777 sleeps 10s; see README for those traces.)

### Step 9 - `saga.reply PAYMENT_AUTHORIZED` (order) + 2 transitions
- `saga.transition INVENTORY_RESERVED->PAYMENT_AUTHORIZED` then `PAYMENT_AUTHORIZED->COMPLETED`.
- Code: `SagaEngine.java:90-97`. Nothing is sent afterwards -> trace ends. No more `publish` spans = saga done.

## 4. The pattern (what to remember)

Each hop is the same 4-span chain:

```
<who sends>  publish  ->  <who receives> process  ->  <who receives> custom span  ->  (DB spans) -> next publish
```

Happy path = 3 hops: order->inventory, inventory->order(+payment cmd), payment->order. The trace is a **ping-pong** with order-service in the middle (orchestrator): it never talks participant-to-participant.

## 5. Quick checklist to verify the trace is right

- [ ] Root is `POST /orders`; all 3 services appear.
- [ ] Custom spans in order: `saga.start` -> `inventory.handle RESERVE_INVENTORY` -> `saga.reply INVENTORY_RESERVED` -> `payment.handle AUTHORIZE_PAYMENT` -> `saga.reply PAYMENT_AUTHORIZED`.
- [ ] 3 `saga.transition` spans after STARTED: `->INVENTORY_RESERVED`, `->PAYMENT_AUTHORIZED`, `->COMPLETED`.
- [ ] Every `idempotency.key` is `<orderId>:<STEP>`.
- [ ] No red (error) spans; no `saga.timeout`.
- [ ] `/saga` timeline matches transitions.

## 6. If it still looks confusing, try these

1. **Search by tag** (Jaeger search page): Service `order-service`, Tags `order.id=<ORDER_ID>`.
2. **Logs for same trace** (one line per milestone):
   ```bash
   docker compose logs | grep e95709e41b76a6191bbd56d45f496a9a | grep -o '"service":"[^"]*"\|"message":"[^"]*"'
   ```
3. **Read code in this order** (matches the trace top to bottom):
   `OrderController` -> `SagaOrchestrator.start` -> `SagaEngine.begin` -> `inventory/CommandListener` -> `ReplyListener` -> `SagaOrchestrator.onReply` -> `SagaEngine.applyReply/transition` -> `payment/CommandListener`.
4. **Compare with a failure** (`order SKU-1 1500`) and diff against the happy trace: the divergence starts at `saga.reply PAYMENT_FAILED`.
5. Why "gaps" in the timeline: between a `publish` end and the `process` start is Kafka poll latency, not work.
