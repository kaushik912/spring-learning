# Tutorial: Span / trace code in Java (OpenTelemetry)

Short, with snippets from this project. Real code: `common/.../SagaTracing.java`.

## 1. Pieces and dependencies

| Piece | What | Where in this project |
|---|---|---|
| **OTel API** (`io.opentelemetry:opentelemetry-api`) | Interfaces you code against: `Span`, `Tracer`, `Context`, `Scope` | `common/pom.xml` (compile dependency) |
| **OTel Java agent** (`opentelemetry-javaagent.jar`) | The real implementation. Attached with `-javaagent`. Auto-creates HTTP, Kafka, JDBC spans, injects `traceparent`, fills log MDC | `Dockerfile` ENTRYPOINT |
| **OTel Collector** | Receives spans from the agent (OTLP), forwards to a backend | `docker/otel-collector.yaml` |
| **Jaeger** | Stores + shows traces (UI :16686) | `docker-compose.yml` |
| `logstash-logback-encoder` | JSON logs; writes MDC fields (`trace_id`, `span_id`, ours `order_id`) | `common/pom.xml` |

Key idea: **your code only depends on the API.** Without the agent, API calls are harmless no-ops (that's why unit/integration tests run fine with no tracing).

```
your code --(API)--> [agent = implementation] --OTLP--> collector --> Jaeger
```

Other ways to get the implementation (not used here): OTel SDK dependency, Spring Boot starter `opentelemetry-spring-boot-starter`, Micrometer Tracing bridge. The API calls below stay the same.

## 2. Vocabulary

- **Trace** = whole request, one `trace_id` (32 hex).
- **Span** = one timed operation inside it, own `span_id` (16 hex), has a **parent** span.
- **Attributes** = key/value tags on a span (`order.id`, `saga.state`).
- **Context** = the "current span" stored in a thread-local. New spans use it as parent.
- **Scope** = handle returned when you make a span current; **must be closed**.
- **traceparent** = W3C header `00-<traceId>-<spanId>-<flags>` that carries context across processes (HTTP header, Kafka record header).

## 3. Recipe A: create a span (the basic flow)

```java
private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("saga-demo");   // once per class

Span span = TRACER.spanBuilder("saga.reply INVENTORY_RESERVED")   // name
        .setAttribute("order.id", orderId)                        // tags
        .startSpan();                                              // parent = current span (if any)
try (Scope ignored = span.makeCurrent()) {                         // make it current for this thread
    doWork();                                                      // children of `span` are created in here
} catch (RuntimeException e) {
    span.recordException(e);                                       // shows as error event in Jaeger
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();                                                    // ALWAYS end, else it is never exported
}
```
Why each line:
- `startSpan()` = begin timer. `end()` = stop timer + queue for export.
- `makeCurrent()` + try-with-resources: JDBC/Kafka/child spans created inside get this span as parent. `Scope.close()` restores the previous one (threads are reused, a leaked scope corrupts later traces).
- This project wraps it once: `SagaTracing.inSpan(span, () -> ...)`.

```java
SagaTracing.inSpan(span, () -> send(engine.applyReply(reply)));   // same as the block above
```

## 4. Recipe B: what the agent gives you for free (no code)

| You do | Agent creates |
|---|---|
| Spring MVC endpoint called | `POST /orders` (server span, root) |
| `kafkaTemplate.send(...)` | `<topic> publish` span + `traceparent` header on the record |
| `@KafkaListener` receives | `<topic> process` span, **child of the publish span** (reads header) |
| `JdbcTemplate.update(...)` | `INSERT orders.saga_instance` etc. |
| logback `log.info(...)` | puts `trace_id`, `span_id`, `trace_flags` into MDC |

So a custom span is only for **business meaning** (`saga.start`, `saga.transition A->B`). Keep it small: name + a few attributes.

## 5. Recipe C: why the trace continues across Kafka (no code from us)

```
order-service:   span saga.start (current)
                   kafka.send(...)  -> agent adds header  traceparent: 00-<traceId>-<publishSpanId>-01
inventory:       agent reads header -> consumer span = child of publishSpanId (same traceId)
                   our inventory.handle span = child of that
                   kafka.send(reply) -> new header, same traceId
order-service:   consumer span -> our saga.reply span ...  (still same traceId)
```
Condition: **send inside the current span** (we call `send(...)` inside `inSpan`), otherwise the producer span has no parent and starts a new trace.

## 6. Recipe D: save context now, resume it later (timeouts)

When work is triggered by a timer, not a message, nothing carries context. So: save it, restore it.

Save (at saga start; `SagaEngine.begin`):
```java
Map<String,String> carrier = new HashMap<>();
W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, Map::put);
String traceParent = carrier.get("traceparent");     // store in DB column trace_parent
```
Restore (scheduler, `SagaTracing.startDetached`):
```java
Context saved = W3CTraceContextPropagator.getInstance()
        .extract(Context.root(), Map.of("traceparent", traceParent), GETTER);
Span span = TRACER.spanBuilder("saga.timeout")
        .setParent(saved)                              // child of the ORIGINAL trace
        .addLink(otherSpanContext)                     // "caused by" that span, not a child of it
        .startSpan();
```
- **Parent** = "I happen inside this span". **Link** = "I'm related to / caused by that span" (shows under *References* in Jaeger).

## 7. Recipe E: suppress noise (unsampled scope)

```java
try (Scope ignored = SagaTracing.unsampled()) {   // parent says "not sampled" -> agent drops child spans
    due = repo.findDue();                          // 1s polling query, stays out of Jaeger
}
```
Creates a fake parent `SpanContext` with `TraceFlags.getDefault()` (= not sampled). Children inherit "not sampled".

## 8. Recipe F: logs <-> traces

Agent puts `trace_id` + `span_id` in MDC automatically; we add business id:
```java
MDC.put("order_id", orderId);
try { ... } finally { MDC.remove("order_id"); }   // thread-local + pooled threads -> always clean up
```
Result: JSON log line has `trace_id`, `span_id`, `order_id` -> `docker compose logs | grep <traceId>`.

## 9. Typical gotchas

- Forgot `span.end()` -> span never appears.
- Forgot to close the `Scope` -> later spans on that thread get the wrong parent.
- Work on **another thread** (async, `@Async`, executor) -> context isn't carried unless you wrap: `Context.current().wrap(runnable)` (the agent does this for common executors).
- Creating a span but not `makeCurrent()` -> children won't attach to it.
- Messages without `traceparent` (e.g. produced by a non-instrumented client) start a new trace.
- Sampling: if the root isn't sampled, the whole trace is dropped. Default sampling in this demo records everything.

## 10. Try it

1. `order SKU-1 100`, open the trace in Jaeger (see `docs/jaeger-happy-path-guide.md`).
2. Read `SagaTracing.java` top to bottom: `start`, `startDetached`, `currentTraceParent`, `unsampled`, `inSpan`. Each maps to one recipe above.
3. In Jaeger, click a `saga.*` span: attributes = recipe A; click `saga.timeout` (777 scenario): References = recipe D.
