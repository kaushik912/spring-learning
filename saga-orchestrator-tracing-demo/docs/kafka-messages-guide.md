# Guide: See the Kafka messages behind the trace

Goal: for one order, see exactly what was sent/received on Kafka, and tie each message to a Jaeger span.

## 0. What flows over Kafka

3 topics, JSON values, **key = orderId** (so all messages of one order go to the same partition, in order).

| Topic | Direction | Value | Types |
|---|---|---|---|
| `inventory-commands` | order -> inventory | `Command` | `RESERVE_INVENTORY`, `RELEASE_INVENTORY` |
| `payment-commands` | order -> payment | `Command` | `AUTHORIZE_PAYMENT`, `VOID_PAYMENT` |
| `order-replies` | inventory/payment -> order | `Reply` | `INVENTORY_RESERVED/REJECTED/RELEASED`, `PAYMENT_AUTHORIZED/FAILED/VOIDED` |

Shapes (`common/.../Command.java`, `Reply.java`):
```json
Command {"commandId":"<orderId>:RESERVE_INVENTORY","orderId":"...","type":"RESERVE_INVENTORY","sku":"SKU-1","qty":1,"amount":100}
Reply   {"commandId":"<orderId>:RESERVE_INVENTORY","orderId":"...","type":"INVENTORY_RESERVED","reason":null}
```
- `commandId` = idempotency key `<orderId>:<STEP>`. A reply echoes the commandId it answers.
- Header `traceparent` = `00-<traceId>-<spanId>-<flags>`, injected by the OTel agent. **This is the link to Jaeger.**

**Happy path = exactly 4 messages:**
```
1 inventory-commands  RESERVE_INVENTORY    order     -> inventory
2 order-replies       INVENTORY_RESERVED   inventory -> order
3 payment-commands    AUTHORIZE_PAYMENT    order     -> payment
4 order-replies       PAYMENT_AUTHORIZED   payment   -> order
```

## 1. Easiest: use the CLI inside the Kafka container (no setup)

The broker only advertises `kafka:9092` (Docker network), so host tools can't connect yet (see section 3). The container's own tools work now:

```bash
cd saga-orchestrator-tracing-demo
K=/opt/kafka/bin

# topics
docker compose exec kafka $K/kafka-topics.sh --bootstrap-server localhost:9092 --list

# replay a topic from the start: partition, offset, headers (traceparent), key, value
docker compose exec kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic inventory-commands --from-beginning --timeout-ms 5000 \
  --property print.key=true --property print.headers=true \
  --property print.partition=true --property print.offset=true
```
Repeat with `payment-commands` and `order-replies`.
(A `TimeoutException` at the end is just the consumer giving up after `--timeout-ms`; normal.)

Filter by one order:
```bash
docker compose exec kafka $K/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic order-replies --from-beginning --timeout-ms 5000 \
  --property print.key=true --property print.headers=true 2>/dev/null | grep <orderId>
```

Real output from this project (message 1):
```
Partition:0  Offset:0  traceparent:00-e95709e41b76a6191bbd56d45f496a9a-85cbce22a7e01e41-03  5000f4f7-...  {"commandId":"5000f4f7-...:RESERVE_INVENTORY","orderId":"5000f4f7-...","type":"RESERVE_INVENTORY","sku":"SKU-1","qty":1,"amount":100}
```

## 2. Tie a message to Jaeger

Take the `traceparent` from a message: `00-<traceId>-<spanId>-01`.
- `traceId` -> `http://localhost:16686/trace/<traceId>`
- `spanId` = the span that **sent** this message: the `<topic> publish` span. In Jaeger search box type the spanId, or find the matching `publish` span.
- Same traceId on all 4 messages of an order = one trace. Different spanId per message = different publish span.

Code that sends each message:
| Message | Sent at |
|---|---|
| 1, 3 (commands) | `SagaOrchestrator.send()` line 298 |
| 2 (inventory reply) | `inventory/CommandListener.java:35` |
| 4 (payment reply) | `payment/CommandListener.java` (`kafka.send(ORDER_REPLIES, ...)`) |

Message consumed at: `inventory/CommandListener.onCommand`, `payment/CommandListener.onCommand`, `order/ReplyListener.onReply`.

## 3. Optional: use your host `kcat` (needs an exposed listener)

`kcat` is installed but can't reach `kafka:9092` from the host. Add a second listener. Create `docker-compose.kafka-host.yml`:

```yaml
services:
  kafka:
    environment:
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:9094
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
    ports:
      - "9094:9094"
```
Start (add `-f docker-compose.debug.yml` too if you use it):
```bash
docker compose -f docker-compose.yml -f docker-compose.kafka-host.yml up -d kafka
```
Services keep using `kafka:9092`; only host tools use `localhost:9094`.

Then:
```bash
kcat -b localhost:9094 -L                                   # topics / partitions
kcat -b localhost:9094 -C -t inventory-commands -o beginning -e \
  -f 'p=%p o=%o key=%k hdr=%h\n%s\n\n'                       # replay with headers
kcat -b localhost:9094 -C -t order-replies -o beginning -e -f '%k %s\n' | grep <orderId>
kcat -b localhost:9094 -C -t order-replies -o end            # live tail while you place an order
```
Live-tail tip: run 3 terminals tailing the 3 topics, then `order SKU-1 100` and watch the 4 messages appear in order.

## 3b. Optional: Kafka UI (browser)

Overlay `docker-compose.kafka-ui.yml`, opt-in via the `ui` profile (a plain `up` does not start it; it costs a few hundred MB RAM).
```bash
docker compose -f docker-compose.yml -f docker-compose.kafka-ui.yml --profile ui up -d kafka-ui
```
Open http://localhost:8090 -> cluster `local` -> Topics -> `order-replies` -> Messages.
- Shows partition, offset, key (orderId), headers (`traceparent`), JSON value. Search box filters by key/text: paste the orderId.
- Consumers tab: lag per group.
- Runs inside the compose network (`kafka:9092`), so it does **not** need the section 3 overlay.
- Stop it when done: `docker compose -f docker-compose.yml -f docker-compose.kafka-ui.yml --profile ui stop kafka-ui`
- Like everything else here, messages vanish when the kafka container is recreated (no volume).

## 4. Other useful checks

```bash
# are consumers keeping up? LAG should be 0 when idle
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group order-service
# groups: order-service, inventory-service, payment-service
```

## 5. Other scenarios, by messages

- Out of stock (`SKU-2`): `RESERVE_INVENTORY` -> `INVENTORY_REJECTED`. 2 messages. No payment topic traffic.
- Payment fails (`amount 1500`): 4 messages as happy path but reply is `PAYMENT_FAILED`, then `RELEASE_INVENTORY` -> `INVENTORY_RELEASED`.
- Timeout (`777`): `RELEASE_INVENTORY`, `VOID_PAYMENT` (resent with backoff, **same commandId** each time = idempotency key), a late `PAYMENT_AUTHORIZED` that order-service ignores, then `PAYMENT_VOIDED`.
  Duplicated `commandId` in `payment-commands` = retries, not bugs.
