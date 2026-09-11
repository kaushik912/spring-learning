# Bug: a slow request with no way to see which step is slow

## The bug

`BuggyOrderService` (`src/main/java/.../buggy/BuggyOrderService.java`) does
four steps to answer one request:

```java
public Map<String, Object> getOrder(String orderId) {
    validate(orderId);                            // ~5ms
    Map<String, Object> order = fetchFromDatabase(orderId); // ~10ms
    double price = callPricingService(orderId);    // ~450ms - the real cost
    return assembleResponse(orderId, order, price); // ~5ms
}
```

The endpoint is slow (~480ms) and Spring's automatic instrumentation still
gives you `http.server.requests` as one aggregate timer and one root trace
span for the whole request — so you genuinely can tell "this endpoint is
slow." What you can't tell is **why**. Open that request's trace in Zipkin
and it's a single flat span with no children. Is it the DB call? The
pricing call? Validation? There's no way to know without reading the
source and guessing, or bisecting with print statements — exactly the
"healthy but slow, no idea where the time goes" problem from the earlier
[consumer-lag-demo](../consumer-lag-demo/bug-fix-notes-consumer-lag.md),
now inside a single request instead of across a stream of them.

## Reproduction

`LatencyObservabilityReproductionTest`:

- `givenBuggyEndpoint_whenCalled_thenNoPerStepTimingIsRecorded` — calls
  `/api/buggy/orders/42` and confirms the `order.pricing` timer's count
  never moves. The 450ms happened; nothing recorded it as its own
  observation.
- `givenFixedEndpoint_whenCalled_thenPerStepTimingRevealsWhichStepDominates`
  — same call shape against `/api/fixed/orders/42`, and now
  `order.pricing`'s recorded time is provably greater than
  `order.validate + order.fetch + order.assemble` combined.

Also verified against the real stack (`docker compose up -d`): both
endpoints measured **~480ms** over curl — identical real latency — but
pulling the trace for `/api/fixed/orders/{id}` from Zipkin's API shows the
breakdown directly:

```
http get /api/fixed/orders/{orderid}   483.8ms
  validate-order                         6.4ms
  fetch-order-from-db                   12.0ms
  call-pricing-service                 451.5ms   <- the answer
  assemble-response                      5.8ms
```

The buggy endpoint's trace for the same 480ms has exactly one span.

## The fix — instrument the steps, not just the endpoint

`FixedOrderService` and its four collaborators (`OrderValidator`,
`OrderDatabaseClient`, `PricingClient`, `OrderResponseAssembler`) do the
identical work with identical timings. The only difference is each
collaborator's method is annotated:

```java
@Observed(name = "order.pricing", contextualName = "call-pricing-service")
public double getPrice(String orderId) {
    sleep(450);
    return 19.99;
}
```

plus one bean registration that makes `@Observed` actually do something:

```java
@Bean
public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
    return new ObservedAspect(observationRegistry);
}
```

That's the whole fix. Micrometer's `ObservationRegistry` is wired to both a
`MeterRegistry` (because `micrometer-registry-prometheus` is on the
classpath) and a `Tracer` (because `micrometer-tracing-bridge-brave` +
`zipkin-reporter-brave` are). One `@Observed` annotation → one
`Observation` per call → both a Prometheus **Timer** and a Zipkin **child
span**, automatically correlated by trace id. Instrument once, get both a
trend view and a per-request view of the same call.

### Gotcha: why four beans instead of four private methods

`@Observed` (like `@Transactional`, `@Cacheable`, etc.) is implemented as a
Spring AOP proxy. A proxy only intercepts calls that arrive **from outside
the bean**, through the proxy. If `getOrder()` called
`this.callPricingService(orderId)` as a private method in the same class,
the annotation would be silently ignored — no error, no exception, just no
observation ever created. That's why the fixed version splits validation,
DB access, pricing, and assembly into four separate `@Component` beans
called from `FixedOrderService`: every call crosses a real bean boundary,
so the proxy actually gets a chance to intercept it. (This also happens to
be more realistic — a validator, a repository, and a downstream client
really would be separate collaborators in most real codebases.)

## Seeing it yourself

```
docker compose up -d          # Zipkin :9411, Prometheus :9090, Grafana :3000
./mvnw spring-boot:run         # app on :8080
curl http://localhost:8080/api/fixed/orders/1
curl http://localhost:8080/api/buggy/orders/1
```

- **Zipkin** (`http://localhost:9411`) — search by service
  `request-latency-tracing-demo`, open a `/api/fixed/orders/{orderid}`
  trace: a waterfall with `call-pricing-service` visibly dwarfing the other
  three spans. Open a `/api/buggy/orders/{orderid}` trace: one bar, no
  children.
- **Prometheus** (`http://localhost:9090`) — query `order_pricing_seconds_sum`,
  `order_validate_seconds_sum`, etc. (Micrometer turns `order.pricing` into
  `order_pricing_seconds_{count,sum,max}`).
- **Grafana** (`http://localhost:3000`, anonymous admin access — local demo
  only, never do this outside your own machine) — the "Request Latency:
  Buggy vs Fixed" dashboard is pre-provisioned with two panels: p95 latency
  for both endpoints side by side (nearly identical — that's the point),
  and the fixed endpoint's per-step breakdown underneath it (where that
  latency actually comes from).

**WSL2 + Docker Desktop note**: Prometheus's scrape target is
`host.docker.internal:8080`, which works on most setups but was
unreachable in this project's own dev environment (WSL2 via Docker
Desktop) — the container needs the app's actual WSL IP. If Prometheus's
target shows `down` at `http://localhost:9090/targets`, find the right
address with `ip addr show eth0` (look for the `inet` line) and swap the
target in `observability/prometheus/prometheus.yml`, then
`docker compose up -d --force-recreate prometheus`.

## Rule of thumb

An aggregate timer on an endpoint tells you *that* something is slow, not
*what*. Once you have more than one meaningfully expensive step in a
request, instrument each step, not just the boundary — a slow-call demo
this simple already needed it, and a real endpoint doing an actual DB
query, an actual downstream HTTP call, and actual business logic needs it
far more. `@Observed` (or manual `Observation` spans) makes that
breakdown exist as data instead of a guess, and — because metrics and
traces share one Observation API in Spring Boot 3+ — costs one annotation
per step, not two separate instrumentation efforts.
