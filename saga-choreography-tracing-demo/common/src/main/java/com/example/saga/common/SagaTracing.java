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
 * without it everything here is a harmless no-op (integration tests).
 * HTTP + Kafka + JDBC spans come from the agent for free. This class adds the custom business
 * spans and the helpers to persist / restore trace context across the outbox.
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

    /** Custom span under the current context (parent-child). Caller makes it current and ends it. */
    public static Span start(String name, String orderId, String eventType, String eventId) {
        SpanBuilder b = TRACER.spanBuilder(name);
        if (orderId != null) b.setAttribute("order.id", orderId);
        if (eventType != null) b.setAttribute("event.type", eventType);
        if (eventId != null) b.setAttribute("event.id", eventId);
        return b.startSpan();
    }

    /**
     * W3C traceparent of the CURRENT span. Called while appending to the outbox so the context survives
     * the DB round-trip (the poller thread has no relationship to the request/consumer thread).
     */
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
     * Make the persisted traceparent the current context. Whatever is created inside (the Kafka producer span,
     * whose traceparent header the agent injects) becomes a child of the span that wrote the outbox row.
     */
    public static Scope restore(String traceParent) {
        if (traceParent == null) {
            return Scope.noop();
        }
        Context ctx = W3CTraceContextPropagator.getInstance()
                .extract(Context.root(), Map.of("traceparent", traceParent), GETTER);
        return ctx.makeCurrent();
    }

    /**
     * Run background polling under an UNSAMPLED parent so the agent's JDBC spans for the
     * "anything in the outbox?" query are dropped (otherwise Jaeger fills with one empty trace per tick).
     */
    public static Scope unsampled() {
        SpanContext sc = SpanContext.create(
                TraceId.fromLongs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), 1),
                SpanId.fromLong(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)),
                TraceFlags.getDefault(), TraceState.getDefault());
        return Span.wrap(sc).makeCurrent();
    }
}
