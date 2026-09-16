# Wise: Total Money Moved in the Past 24 Hours (Real-Time Streaming)

> Wise, a payments company, wishes to get the total amount of money moved in the past 24
> hours. Payments happen in USD, EUR, or GBP. Assume a currency conversion service gives
> real-time conversion rates (mocked here). Is there a way to do this real-time using
> streaming logic?

This project answers that with a working Spring Boot + Kafka Streams app: payments are
converted to USD and folded into a continuously-updated rolling total the instant they
arrive, queryable over REST at any moment — no batch job, no re-scan of history.

## Functionality

- Payments are submitted (via a REST endpoint, or a built-in simulator) in USD, EUR, or GBP.
- Each payment is converted to USD using the FX rate that was in effect **at the payment's
  own timestamp**, not "now" (see [Correctness note](#correctness-note-which-fx-rate-to-use)).
- A Kafka Streams topology maintains a continuously-updated rolling USD total, queryable
  at any instant.

### Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/payments` | Body `{ "amount": 1250.00, "currency": "EUR", "timestamp": "2026-09-16T08:00:00Z" }` (`timestamp` optional, defaults to now — backdate it to see a different FX rate applied). `202` with the accepted payment. |
| GET | `/api/payments/volume` | Returns the still-filling current window and the latest fully-closed window, both in USD. |

Swagger UI: `/swagger-ui.html` — OpenAPI spec: `/v3/api-docs` (springdoc-openapi).

## Discussion: can this be done real-time via streaming?

**Yes.** A Kafka Streams windowed aggregation folds each new payment into a materialized
running total the moment it's consumed. There's no polling loop and no re-reading of
history — the total is always already computed, sitting in a local state store, ready to
be read.

**Batch vs. streaming.** A nightly or hourly batch job (`SELECT SUM(usd_amount) WHERE
timestamp > now() - interval '24 hours'`) gives a correct answer, but a stale one — it's
only as fresh as the last run, and it re-scans a growing table every time it runs. The
streaming approach trades a small, bounded amount of staleness for near-instant freshness,
at the cost of more moving infrastructure (a broker, a stream-processing app, local state).
For a payments company that wants a live "money moved" dashboard rather than a report,
that trade is usually worth it.

**Why hopping windows, not a true sliding window.** The topology uses Kafka Streams
`TimeWindows` **hopping windows**: size = 24h (2m in the `demo` profile), advance = 1h
(10s in `demo`). Hopping windows sit on a fixed grid, which is the standard, well-documented
shape for Interactive Query range scans against a `WindowStore`. The cost: no single window
is ever exactly `[now-24h, now)` — the freshest available total is stale by up to one
advance interval (up to 1h in the default config). A `SlidingWindows` (KIP-450) topology
would give an exact trailing-24h total keyed to each record's own timestamp, but at the
cost of materializing many more overlapping window segments and a less standard Interactive
Query pattern. Given the 24h/1h ratio here, up-to-1h staleness is an acceptable, well-known
trade for a much simpler and cheaper implementation — that's why hopping windows were
chosen, not because sliding windows are wrong.

## Architecture

```
PaymentController (POST) ---\
                              \
PaymentSimulator (@Scheduled) -+--> Kafka topic "payment-events" --> Kafka Streams topology:
                                        map to USD (FX rate at event time)
                                        --> windowedBy(hopping 24h/1h)
                                        --> aggregate into WindowStore "payment-volume-store"
                                                                              |
                              PaymentVolumeController (GET) <-- Interactive Query
```

**Why Kafka Streams DSL + Interactive Queries, not a plain `@KafkaListener` + in-memory
map** (the style every other Kafka demo in this repo uses): the windowed state store is
backed by RocksDB and replicated via a Kafka changelog topic, so the running total survives
an app restart without replaying the full topic from scratch. The query path
(`PaymentVolumeController`) is also fully decoupled from the ingestion path (`PaymentController`
/ `PaymentSimulator`) — reading the total never touches the consumer, and a slow query can't
back up ingestion.

## Correctness note: which FX rate to use

A payments company must value a past transfer using the FX rate that applied **when it
happened**, not today's rate — otherwise "money moved yesterday" silently changes value
every time you ask. Two pieces of this project enforce that:

- `PaymentEventTimestampExtractor` forces Kafka Streams to window on the payment's own
  `timestamp` field (event time), not on when Kafka happened to receive it.
- `MockCurrencyConversionService` keeps an immutable, append-only history of rate
  snapshots and resolves `convertToUsd(amount, currency, eventTime)` via a floor lookup —
  the latest snapshot **at or before** `eventTime`. A new snapshot published later never
  changes how an earlier payment converts.

You can see this directly: `POST` a payment with a `timestamp` from before the app started
(or before the last simulated rate jitter) and it converts using the older rate, not the
current one.

## Scaling caveat: single-instance state

Kafka Streams state stores are partition-local — a single running instance here holds the
entire store (the topic has 1 partition since every event is re-keyed to one constant
aggregate key anyway, so more partitions wouldn't add parallelism). This is forward-looking,
not implemented, but worth naming: in a multi-instance deployment, `KafkaStreamsInteractiveQueryService`
(and the underlying `KafkaStreams.metadataForKey`/host-info APIs it wraps) is how a query
would be routed to whichever instance actually owns the partition holding the requested key,
rather than assuming the local store has the answer.

## Key classes / modules

| Package | Class | Responsibility |
|---|---|---|
| `model` | `PaymentEvent`, `Currency` | The event shape and supported currencies |
| `config` | `PaymentsProperties` | Binds `payments.window.*` / `payments.fx.*` / `payments.simulator.*` |
| `conversion` | `CurrencyConversionService` / `MockCurrencyConversionService` | Rate-at-event-time FX conversion (see [Correctness note](#correctness-note-which-fx-rate-to-use)) |
| `kafka` | `PaymentTopics`, `KafkaTopicsConfig` | Topic name/constants, topic provisioning |
| `kafka` | `PaymentEventTimestampExtractor` | Makes windowing use event time, not ingestion time |
| `kafka` | `BigDecimalSerde` | Custom Serde — Kafka Streams has no built-in one, and money isn't a `double` |
| `streams` | `PaymentVolumeTopology` | The topology itself: convert → window → aggregate; also registers `KafkaStreamsInteractiveQueryService` |
| `simulator` | `PaymentSimulator` | `@Scheduled` producer standing in for real traffic |
| `web` | `PaymentController` | `POST /api/payments` |
| `web` | `PaymentVolumeController` | `GET /api/payments/volume` — the Interactive Query read path |

## Testing

| Layer | Tool | What it covers |
|---|---|---|
| FX conversion | Plain JUnit | Rate-at-event-time resolution, USD passthrough, pre-history fallback, immutability of past snapshots |
| Timestamp extraction | Plain JUnit | Event time used over partition time |
| Streams topology | `TopologyTestDriver` (no broker) | Multi-currency aggregation, late-arrival within grace, exclusion past grace — with precise, controlled timestamps |
| Full stack | `@SpringBootTest` + `@EmbeddedKafka` + `TestRestTemplate` | One true end-to-end test: real produce → real topology → real REST read, via bounded-retry polling (not a fixed sleep) |

`TopologyTestDriver` is used for the DSL logic itself since it needs no broker;
`@EmbeddedKafka` is reserved for the single producer→topology→REST integration test that
actually needs one.

```bash
./mvnw test
```

## Setup / run

```bash
# 1. Start Kafka (KRaft, single broker)
docker compose up -d

# 2. Run the app - production-shaped config (24h window, 1h advance)
./mvnw spring-boot:run

# ...or the demo profile - 2-minute window, 10s advance, 5s FX jitter,
# so a window rollover and an FX-rate change are both visible within ~2-3 minutes
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

```bash
# Submit a payment (backdate the timestamp to see an older FX rate applied)
curl -X POST localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{"amount": 1250.00, "currency": "EUR", "timestamp": "2026-09-16T08:00:00Z"}'

# Query the rolling total
curl localhost:8080/api/payments/volume
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

**Note on `spring.kafka.streams.application-id`:** Kafka Streams persists local state keyed
by application-id. The `demo` profile uses a different application-id
(`payment-volume-streaming-demo-demo`) specifically so switching between profiles never
mixes a 24h-window state directory with a 2-minute-window one.
