# Gateway routing works; full cross-service partial-failure traced and confirmed

Gateway routing verified (customer-service reachable via localhost:8222, same path as its own port). Full `createOrder` attempted through the gateway — failed exactly as predicted: `PaymentClient#requestOrderPayment` threw `FeignException.InternalServerError` because Payment's own Kafka-notify step 500'd (same root cause as Lesson 4, now surfacing one hop further up the call chain).

Verified via direct `psql` across all three databases: Order/OrderLine rolled back (0 rows, `@Transactional` unwind on uncaught exception) but Product's stock decrement and Payment's row both persisted — and Payment's `order_id` points at an Order id that was allocated in-memory then rolled back, i.e. a dangling reference to an order that never existed in Order's own tables. User predicted the stock-not-rolled-back part correctly before checking; the orphaned `order_id` detail was new and confirmed live.

**Implication**: user can now reason about cross-service transaction boundaries unprompted (predicted the failure shape before running it). Section 13 (fixing Kafka) should be framed not just as "notifications work now" but as "this is what actually makes `createOrder` succeed at all, since Payment's uncaught Kafka failure is what breaks the whole chain."
