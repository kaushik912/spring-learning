# Known Issues — found while learning, to fix on a separate branch

All found hands-on while working through `../reference_repos/fully-completed-microservices-Java-Springboot`
lesson by lesson (see `lessons/`). Repo root below = that repo's root.

## Already fixed (uncommitted, in the working tree right now)

- [x] **Kafka wouldn't start.** `docker-compose.yml` pinned `confluentinc/cp-kafka:latest` /
  `cp-zookeeper:latest`. `latest` now means Kafka 4.x (KRaft-only), but the compose file's env
  vars configure old Zookeeper-mode — crash loop. **Fix applied:** pinned both images to `7.6.1`
  in `docker-compose.yml`.
- [x] **Kafka producer hung 60s when broker was down.** Default `max.block.ms` is 60000ms.
  **Fix applied:** added `max.block.ms: 3000` under `spring.kafka.producer.properties` in
  `services/config-server/src/main/resources/configurations/payment-service.yml` and
  `order-service.yml`.

Run `git diff` in the repo root to see the exact changes. Not committed yet.

## Still open

1. **`OrderMapper.toOrder` never sets `totalAmount`.**
   `services/order/.../order/OrderMapper.java` — the builder never calls
   `.totalAmount(request.amount())`. Every order saves with `totalAmount = null`, and every
   `GET` on it reports `"amount": null` regardless of what was sent in.
   → Fix: add `.totalAmount(request.amount())` to the builder.

2. **`PaymentClient`'s `@FeignClient` name is wrong.**
   `services/order/.../payment/PaymentClient.java` — `name = "product-service"`, should be
   `"payment-service"`. Harmless today (a hardcoded `url` overrides `name` for routing), but a
   landmine if `url` is ever dropped in favor of pure Eureka-name resolution — calls would
   silently misroute to wherever `product-service` is registered.
   → Fix: change `name` to `"payment-service"`.

3. **Per-service Postgres databases aren't auto-created.**
   The `postgresql` container in `docker-compose.yml` only auto-creates a DB named after
   `POSTGRES_USER` (`alibou`). `order`, `payment`, `product` (each service's `spring.datasource.url`)
   don't exist until someone runs `CREATE DATABASE` manually.
   → Fix: add a Postgres init script (mount into `/docker-entrypoint-initdb.d/`) that creates
   all three databases on first container start.

4. **No error handling around `OrderService.createOrder`'s outbound calls.**
   `services/order/.../order/OrderService.java` — calls to `customerClient`, `productClient`,
   `paymentClient` are all unguarded. Any downstream failure (404, 500, timeout, whatever)
   becomes a generic uncaught 500 back to the original caller. No `@ExceptionHandler` for
   `FeignException`/`RestClientException` in `GlobalExceptionHandler`.
   → Fix: catch each call, map to a sensible client-facing error (e.g. 404 "customer not found"
   stays a 404, not a 500).

5. **Dual-write / partial-failure across services — structural, not a one-line fix.**
   `createOrder` calls Product (decrements stock, commits immediately elsewhere) *before*
   Order/OrderLine/Payment finish. If anything after that fails — duplicate `reference` (#6),
   Payment down, Kafka down — Product's stock decrement is never undone, and Payment can end
   up with a row pointing at an Order id that got rolled back.
   → Real fix: outbox pattern (write intent-to-publish in the same local transaction, poll and
   publish separately) and/or a saga with compensating actions for the Product-stock step.
   Bigger effort — worth its own design pass, not a quick patch.

6. **Duplicate `reference` triggers #5's failure deterministically.**
   `Order.reference` has `@Column(unique = true)`. Reusing a reference throws a DB constraint
   violation at `repository.save(...)` — by which point Product's stock is already decremented
   (see #5). Same root issue as #5, just a guaranteed way to trigger it without needing
   anything actually down.

7. **Notification's Kafka consumer `group-id` is one malformed name, not two groups.**
   `services/notification/.../notification-service.yml` —
   `group-id: paymentGroup,orderGroup`. Reads like two groups; Spring's `group-id` takes one
   string, so the real group is the literal `"paymentGroup,orderGroup"`. Harmless today (one
   process, two listeners sharing one group is fine), but misleading and would break if
   payment/order consumption were ever split into separate services.
   → Fix: pick one real group id, or configure per-listener `groupId` explicitly on each
   `@KafkaListener`.

8. **No distributed trace propagation out of order-service.**
   Confirmed via Zipkin: order-service's outbound calls to Customer (Feign), Payment (Feign),
   and Product (RestTemplate) don't carry the current trace context forward — each downstream
   hop starts a *new* root trace at the gateway instead of continuing the original one. Likely
   causes:
   - `services/order/.../config/RestTemplateConfig.java` builds `new RestTemplate()` directly
     instead of via `RestTemplateBuilder`, which is what Spring Boot auto-instruments for
     tracing.
   - Feign clients (`CustomerClient`, `PaymentClient`) likely lack the tracing bridge needed for
     automatic B3/`traceparent` header propagation (no `feign-micrometer`-equivalent wiring
     found).
   → Fix: build the `RestTemplate` bean via `RestTemplateBuilder`; add/verify Feign's
   micrometer-tracing integration. Verify by repeating the trace-comparison check from Lesson
   10 (same `traceId` should then show up across gateway → order-service → customer/product/
   payment-service).

## Not a bug — missing feature

9. **No API docs (Swagger/springdoc-openapi) anywhere.** Curriculum Section 17 covers exposing
   each service's API docs through the gateway — this repo never implemented it. No
   `springdoc-openapi` dependency on any service, no docs route on the gateway.
   → Fix (when picked up): add `springdoc-openapi-starter-webmvc-ui` to each service, then a
   gateway route per service exposing `/v3/api-docs`/`/swagger-ui.html`, similar to the existing
   `Path=/api/v1/...` routes in `gateway-service.yml`.

## Background: the two patterns #5 needs

Both come up because `createOrder` touches multiple services' databases with no way to make
them commit or roll back together (there's no such thing as a transaction that spans two
separate databases/services here).

**Outbox pattern** — fixes "the DB write succeeded but the Kafka publish didn't" (also seen in
Payment, Lesson 4). Instead of writing to the DB *and then* publishing to Kafka as two separate
steps, write the event into an `outbox` table in the **same local transaction** as the real
write (e.g. the `Payment` row). A separate background poller reads unpublished outbox rows and
sends them to Kafka, retrying until it succeeds, then marks them done. Since the outbox row and
the business row commit or roll back together, the notification can never be silently lost —
worst case it's late, never missing. Where it'd apply here: Payment's `NotificationProducer` call
and Order's `OrderProducer` call.

**Saga pattern** — fixes "Product's stock got decremented but the order never actually got
created" (#5/#6). A saga is a sequence of local transactions across services, where *every* step
has a defined **compensating action** to undo it if a later step fails. For `createOrder`, that
would mean: if Order/OrderLine save fails or Payment fails, explicitly call a compensating
"release stock" endpoint on Product to undo the earlier decrement — rather than assuming it'll
sort itself out (it currently doesn't). Two common flavors: choreography (each service listens
for a "this step failed" event and reacts on its own) or orchestration (one coordinator service
explicitly drives every step and every compensation). For a repo this size, orchestration inside
`OrderService` itself (a plain try/catch that calls a new `productClient.releaseStock(...)` on
failure) would be the simplest version — doesn't need new infrastructure, just new endpoints and
explicit failure handling.

Neither pattern is implemented anywhere in this repo today — both are genuinely new work, not a
missed setting.

## Notes for whoever picks this up

- This is a *fully completed* reference repo (one big "completed" commit, no incremental
  history) — these aren't regressions, they're pre-existing gaps in the original code.
- Found via `~/github_projs/spring-learning/learning-microservices/lessons/0001` through `0010` — each lesson
  has the full context (what was tried, what broke, what the evidence showed) if more detail is
  needed than this summary.
