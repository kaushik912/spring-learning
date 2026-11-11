package com.example.saga.inventory;

import com.example.saga.common.Command;
import com.example.saga.common.Reply;
import com.example.saga.common.ReplyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final JdbcTemplate jdbc;

    public InventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent: if this commandId was already processed we do NOT touch stock again,
     * we just replay the stored reply (the orchestrator may have retried because our reply was slow/lost).
     */
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
            case RESERVE_INVENTORY -> reserve(c);
            case RELEASE_INVENTORY -> release(c);
            default -> throw new IllegalArgumentException("not an inventory command: " + c.type());
        };
        jdbc.update("insert into processed_command (command_id, reply_type, reason) values (?,?,?)",
                c.commandId(), reply.type().name(), reply.reason());
        return reply;
    }

    private Reply reserve(Command c) {
        // atomic check-and-decrement, no read-then-write race
        int rows = jdbc.update("update inventory set available = available - ? where sku=? and available >= ?",
                c.qty(), c.sku(), c.qty());
        if (rows == 0) {
            log.info("order {} inventory REJECTED sku={} qty={} (out of stock)", c.orderId(), c.sku(), c.qty());
            return new Reply(c.commandId(), c.orderId(), ReplyType.INVENTORY_REJECTED, "out of stock: " + c.sku());
        }
        jdbc.update("insert into reservation (order_id, sku, qty, status) values (?::uuid,?,?,'RESERVED')",
                c.orderId(), c.sku(), c.qty());
        log.info("order {} inventory RESERVED sku={} qty={}", c.orderId(), c.sku(), c.qty());
        return new Reply(c.commandId(), c.orderId(), ReplyType.INVENTORY_RESERVED, null);
    }

    private Reply release(Command c) {
        // Only give stock back if we really hold a reservation (release may arrive for an order we never reserved)
        int rows = jdbc.update("update reservation set status='RELEASED' where order_id=?::uuid and status='RESERVED'",
                c.orderId());
        if (rows == 1) {
            jdbc.update("update inventory set available = available + ? where sku=?", c.qty(), c.sku());
            log.info("order {} inventory RELEASED sku={} qty={}", c.orderId(), c.sku(), c.qty());
        } else {
            log.info("order {} inventory release: nothing reserved, no-op", c.orderId());
        }
        return new Reply(c.commandId(), c.orderId(), ReplyType.INVENTORY_RELEASED, null);
    }
}
