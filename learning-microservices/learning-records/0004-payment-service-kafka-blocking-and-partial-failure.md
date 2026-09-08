# Corrected: Kafka producer send() blocks on first call; payment writes survive a 500

Initially told the user `kafkaTemplate.send()` never blocks the HTTP response — wrong for the case where Kafka is unreachable. In practice `KafkaProducer.send()` blocks up to `max.block.ms` (default 60s) fetching topic metadata before returning, so with Kafka down `createPayment` hung the whole `max.block.ms` window. Fixed by setting `max.block.ms: 3000` in `payment-service.yml` (config-server) for fast-fail during this teaching window; proper Kafka fix deferred to Section 13 as planned.

User then hit and verified, hands-on, a related real finding: `repository.save(...)` commits before the Kafka call, so the payment row was persisted in Postgres despite the client receiving a 500 — confirmed via direct `psql SELECT`. Folded both findings into Lesson 4 (`0004-payment-service.html`) as verified callouts rather than speculative ones.

**Implication**: user is engaging critically with failure modes, not just happy-path CRUD — future lessons can lean into "what happens when X is down" as a teaching device rather than avoiding it. Also: Order service (Lesson 3) will very likely hit the same `max.block.ms` hang once we test its Kafka publish in a later lesson — apply the same fix to `order-service.yml` proactively before that lesson's hands-on step.
