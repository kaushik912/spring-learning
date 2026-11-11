package com.example.saga.order;

import com.example.saga.common.CommandType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SagaRepository {

    private static final RowMapper<Saga> MAPPER = (rs, i) -> {
        Saga s = new Saga();
        s.id = rs.getObject("id", UUID.class);
        s.orderId = rs.getObject("order_id", UUID.class);
        s.sku = rs.getString("sku");
        s.qty = rs.getInt("qty");
        s.amount = rs.getLong("amount");
        s.state = SagaState.valueOf(rs.getString("state"));
        String step = rs.getString("current_step");
        s.currentStep = step == null ? null : CommandType.valueOf(step);
        s.needsRelease = rs.getBoolean("needs_release");
        s.needsVoid = rs.getBoolean("needs_void");
        s.attempts = rs.getInt("attempts");
        Timestamp next = rs.getTimestamp("next_action_at");
        s.nextActionAt = next == null ? null : next.toInstant();
        s.traceId = rs.getString("trace_id");
        s.traceParent = rs.getString("trace_parent");
        s.stepTraceParent = rs.getString("step_trace_parent");
        return s;
    };

    private final JdbcTemplate jdbc;

    public SagaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Saga s) {
        jdbc.update("""
                insert into saga_instance (id, order_id, sku, qty, amount, state, current_step, needs_release,
                  needs_void, attempts, next_action_at, trace_id, trace_parent, step_trace_parent)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                s.id, s.orderId, s.sku, s.qty, s.amount, s.state.name(), name(s.currentStep), s.needsRelease,
                s.needsVoid, s.attempts, ts(s.nextActionAt), s.traceId, s.traceParent, s.stepTraceParent);
    }

    void update(Saga s) {
        jdbc.update("""
                update saga_instance set state=?, current_step=?, needs_release=?, needs_void=?, attempts=?,
                  next_action_at=?, step_trace_parent=?, updated_at=now() where id=?""",
                s.state.name(), name(s.currentStep), s.needsRelease, s.needsVoid, s.attempts,
                ts(s.nextActionAt), s.stepTraceParent, s.id);
    }

    /** Row lock: reply consumer and timeout scheduler must not mutate the same saga concurrently. */
    Optional<Saga> lockByOrderId(UUID orderId) {
        return jdbc.query("select * from saga_instance where order_id=? for update", MAPPER, orderId)
                .stream().findFirst();
    }

    Optional<Saga> findByOrderId(UUID orderId) {
        return jdbc.query("select * from saga_instance where order_id=?", MAPPER, orderId).stream().findFirst();
    }

    List<Saga> findDue() {
        return jdbc.query("select * from saga_instance where next_action_at is not null and next_action_at <= now()",
                MAPPER);
    }

    void log(Saga s, SagaState from, SagaState to, CommandType step, String detail, String traceId) {
        jdbc.update("insert into saga_step_log (saga_id, from_state, to_state, step, detail, trace_id) values (?,?,?,?,?,?)",
                s.id, from == null ? null : from.name(), to.name(), name(step), detail, traceId);
    }

    List<Map<String, Object>> timeline(UUID sagaId) {
        return jdbc.queryForList("""
                select from_state, to_state, step, detail, trace_id, created_at
                from saga_step_log where saga_id=? order by id""", sagaId);
    }

    private static String name(CommandType t) {
        return t == null ? null : t.name();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
