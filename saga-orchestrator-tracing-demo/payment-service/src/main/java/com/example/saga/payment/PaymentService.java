package com.example.saga.payment;

import com.example.saga.common.Command;
import com.example.saga.common.Reply;
import com.example.saga.common.ReplyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final JdbcTemplate jdbc;
    private final long maxAmount;
    private final long timeoutAmount;
    private final Duration timeoutSleep;

    public PaymentService(JdbcTemplate jdbc,
                          @Value("${payment.max-amount}") long maxAmount,
                          @Value("${payment.timeout-amount}") long timeoutAmount,
                          @Value("${payment.timeout-sleep}") Duration timeoutSleep) {
        this.jdbc = jdbc;
        this.maxAmount = maxAmount;
        this.timeoutAmount = timeoutAmount;
        this.timeoutSleep = timeoutSleep;
    }

    /** Idempotent: a repeated commandId replays the stored reply, no second authorization / void. */
    @Transactional
    public Reply handle(Command c) {
        List<Reply> prior = jdbc.query(
                "select reply_type, reason from processed_command where command_id=?",
                (rs, i) -> new Reply(c.commandId(), c.orderId(), ReplyType.valueOf(rs.getString(1)), rs.getString(2)),
                c.commandId());
        if (!prior.isEmpty()) {
            log.debug("duplicate command {} -> replaying {}", c.commandId(), prior.get(0).type());
            return prior.get(0);
        }
        Reply reply = switch (c.type()) {
            case AUTHORIZE_PAYMENT -> authorize(c);
            case VOID_PAYMENT -> voidPayment(c);
            default -> throw new IllegalArgumentException("not a payment command: " + c.type());
        };
        jdbc.update("insert into processed_command (command_id, reply_type, reason) values (?,?,?)",
                c.commandId(), reply.type().name(), reply.reason());
        return reply;
    }

    private Reply authorize(Command c) {
        if (c.amount() == timeoutAmount) {
            // Simulated slow gateway. The orchestrator's step timeout (5s) fires first and compensates;
            // this still completes later -> the late reply is ignored and the VoidPayment that was queued
            // behind us (same partition) then voids what we just authorized.
            log.debug("order {} simulating gateway timeout, sleeping {}", c.orderId(), timeoutSleep);
            try {
                Thread.sleep(timeoutSleep.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (c.amount() > maxAmount) {
            log.info("order {} payment FAILED amount={} (limit {})", c.orderId(), c.amount(), maxAmount);
            return new Reply(c.commandId(), c.orderId(), ReplyType.PAYMENT_FAILED, "amount exceeds limit " + maxAmount);
        }
        jdbc.update("insert into payment (order_id, amount, status) values (?::uuid,?,'AUTHORIZED')",
                c.orderId(), c.amount());
        log.info("order {} payment AUTHORIZED amount={}", c.orderId(), c.amount());
        return new Reply(c.commandId(), c.orderId(), ReplyType.PAYMENT_AUTHORIZED, null);
    }

    private Reply voidPayment(Command c) {
        int rows = jdbc.update("update payment set status='VOIDED' where order_id=?::uuid", c.orderId());
        if (rows == 0) {
            // void arrived before/without an authorization: record a tombstone so it can't be authorized later
            jdbc.update("insert into payment (order_id, amount, status) values (?::uuid,?,'VOIDED')",
                    c.orderId(), c.amount());
        }
        log.info("order {} payment VOIDED", c.orderId());
        return new Reply(c.commandId(), c.orderId(), ReplyType.PAYMENT_VOIDED, null);
    }
}
