package com.example.saga.flowtracker;

import com.example.saga.common.Event;
import com.example.saga.common.EventProcessor;
import com.example.saga.common.EventTypes;
import com.example.saga.common.SagaTracing;
import com.example.saga.common.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Subscribes to every topic and stores one row per event. Pure observer: never publishes. */
@Component
class TimelineRecorder {

    private final EventProcessor processor;
    private final JdbcTemplate jdbc;
    private final Map<String, Consumer<Event>> handlers =
            EventTypes.ALL.stream().collect(Collectors.toMap(t -> t, t -> this::record));

    TimelineRecorder(EventProcessor processor, JdbcTemplate jdbc) {
        this.processor = processor;
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS})
    void on(ConsumerRecord<String, String> record) {
        processor.process(record, handlers);
    }

    private void record(Event e) {
        // TRACING: the trace id of the span we are running in. It equals the trace of the original POST only if
        // context propagation worked end to end - so this column doubles as a propagation check.
        jdbc.update("""
                        insert into event_timeline(event_id, order_id, event_type, service, trace_id, occurred_at)
                        values (?, ?, ?, ?, ?, ?) on conflict do nothing""",
                e.eventId(), e.orderId(), e.eventType(), e.producer(), SagaTracing.currentTraceId(),
                Timestamp.from(e.occurredAt()));
    }
}
