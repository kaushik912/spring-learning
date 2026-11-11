package com.example.saga.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thin wrapper over the OTel API. The Java agent (-javaagent) provides the real implementation;
 * without it everything here is a harmless no-op (unit tests).
 * HTTP + Kafka + JDBC spans come from the agent for free. This class only adds the custom
 * saga spans and the helpers to persist / restore trace context.
 */
public final class SagaTracing {

    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("saga-demo");

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private SagaTracing() {
    }

    /** Custom span under the current context, tagged with the saga attributes. Caller must end it. */
    public static Span start(String name, String orderId, String state, String step, String idempotencyKey) {
        return builder(name, orderId, state, step, idempotencyKey).startSpan();
    }

    /**
     * Span for work NOT triggered by a message (timeout / retry scheduler): no natural parent.
     * - PARENT = trace context saved when the saga started, so the compensation lands in the
     *   same trace as the original POST.
     * - LINK   = span that sent the command that never got answered ("caused by", not "child of").
     */
    public static Span startDetached(String name, String orderId, String state, String step,
                                     String idempotencyKey, String parentTraceParent, String linkTraceParent) {
        SpanBuilder b = builder(name, orderId, state, step, idempotencyKey);
        if (parentTraceParent != null) {
            b.setParent(extract(parentTraceParent));
        }
        SpanContext link = spanContext(linkTraceParent);
        if (link.isValid()) {
            b.addLink(link);
        }
        return b.startSpan();
    }

    /** W3C traceparent of the current span, to persist (e.g. in saga_instance). Null if no active trace. */
    public static String currentTraceParent() {
        Map<String, String> carrier = new HashMap<>();
        W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, Map::put);
        return carrier.get("traceparent");
    }

    public static String currentTraceId() {
        SpanContext sc = Span.current().getSpanContext();
        return sc.isValid() ? sc.getTraceId() : null;
    }

    /**
     * Run background polling under an UNSAMPLED parent so the agent's JDBC spans for the
     * "any saga due?" query are dropped (otherwise Jaeger fills with one empty trace per second).
     */
    public static Scope unsampled() {
        SpanContext sc = SpanContext.create(
                TraceId.fromLongs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), 1),
                SpanId.fromLong(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)),
                TraceFlags.getDefault(), TraceState.getDefault());
        return Span.wrap(sc).makeCurrent();
    }

    private static SpanBuilder builder(String name, String orderId, String state, String step, String key) {
        SpanBuilder b = TRACER.spanBuilder(name);
        if (orderId != null) b.setAttribute("order.id", orderId);
        if (state != null) b.setAttribute("saga.state", state);
        if (step != null) b.setAttribute("saga.step", step);
        if (key != null) b.setAttribute("idempotency.key", key);
        return b;
    }

    private static Context extract(String traceParent) {
        return W3CTraceContextPropagator.getInstance()
                .extract(Context.root(), Map.of("traceparent", traceParent), GETTER);
    }

    private static SpanContext spanContext(String traceParent) {
        return traceParent == null ? SpanContext.getInvalid() : Span.fromContext(extract(traceParent)).getSpanContext();
    }
}
