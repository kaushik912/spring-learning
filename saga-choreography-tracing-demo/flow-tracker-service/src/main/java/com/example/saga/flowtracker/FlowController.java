package com.example.saga.flowtracker;

import com.example.saga.common.EventTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
class FlowController {

    record Step(String eventType, String service, String traceId, Instant occurredAt) {
    }

    record Flow(String orderId, String status, String traceId, List<Step> timeline) {
    }

    private static final String TERMINAL_SQL = EventTypes.TERMINAL.stream()
            .map(t -> "'" + t + "'").collect(Collectors.joining(","));

    private final JdbcTemplate jdbc;

    FlowController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/flows/{orderId}")
    ResponseEntity<Flow> flow(@PathVariable String orderId) {
        List<Step> steps = jdbc.query("""
                        select event_type, service, trace_id, occurred_at from event_timeline
                        where order_id = ? order by occurred_at, id""",
                (rs, i) -> new Step(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant()), orderId);
        if (steps.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String status = steps.stream().map(Step::eventType)
                .filter(EventTypes.TERMINAL::contains).findFirst()
                .map(t -> t.substring("Order".length()).toUpperCase())   // OrderConfirmed -> CONFIRMED
                .orElse("IN_PROGRESS");
        String traceId = steps.stream().map(Step::traceId).filter(t -> t != null).findFirst().orElse(null);
        return ResponseEntity.ok(new Flow(orderId, status, traceId, steps));
    }

    /** Stuck = first event older than N seconds and no terminal event (OrderConfirmed/Rejected/Cancelled) yet. */
    @GetMapping("/flows/stuck")
    List<Map<String, Object>> stuck(@RequestParam(defaultValue = "30") long olderThanSeconds) {
        return jdbc.queryForList("""
                select order_id as "orderId",
                       min(occurred_at) as "startedAt",
                       (array_agg(event_type order by occurred_at desc, id desc))[1] as "lastEvent",
                       max(trace_id) as "traceId"
                from event_timeline
                group by order_id
                having min(occurred_at) < ?
                   and bool_or(event_type in (%s)) = false
                order by min(occurred_at)""".formatted(TERMINAL_SQL),
                Timestamp.from(Instant.now().minusSeconds(olderThanSeconds)));
    }
}
