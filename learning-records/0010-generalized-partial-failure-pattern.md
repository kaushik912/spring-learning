# Generalized the partial-failure pattern beyond Kafka specifically

User independently found a second trigger for the same class of bug from Lesson 7: reusing an order `reference` throws a unique-constraint violation at `repository.save(...)` (step 3 of `createOrder`), but `productClient.purchaseProducts(...)` (step 2) already committed the stock decrement in Product's own database beforehand. Confirmed the mechanism matches Lesson 7 exactly, just triggered deterministically (duplicate key) rather than by Kafka being down.

Explained the generalization explicitly: this isn't a Kafka-specific problem — any failure after Product's call leaves its side effect stranded, because `@Transactional` on `createOrder` only ever covered Order's own local database, never the already-committed remote call. User accepted this framing without needing the saga/outbox terms re-explained (already internalized from Lessons 4 and 7).

**Implication**: user now spontaneously discovers instances of a pattern once taught, rather than needing each occurrence pointed out — can shift from "here's a bug, here's why" to "you tell me why" in later lessons when a similar shape recurs (e.g. Section 16 tracing will likely surface more of these, and it's fair to let the user spot them first).
