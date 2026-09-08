# Feign vs RestTemplate + Product service verified hands-on

Ran Product service standalone (Flyway seed data, 25 products), exercised `/purchase` directly (stock decrement) and the insufficient-stock error path. Confirmed understanding that Kafka's down state is scoped to Order/Payment only — asked proactively whether it'd block this lesson before running it, correctly reasoning Product never touches Kafka. Feign (`CustomerClient`/`PaymentClient`) vs RestTemplate (`ProductClient`) comparison and the `PaymentClient` `@FeignClient(name=...)` typo landed without pushback.

**Implication**: user is now tracking which services depend on which infra pieces (Kafka, Eureka, Gateway) rather than assuming failures are global — future lessons can skip re-explaining "is X required" scoping unless a new dependency is introduced.
