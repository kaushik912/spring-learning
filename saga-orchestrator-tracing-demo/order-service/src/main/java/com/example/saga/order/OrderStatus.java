package com.example.saga.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Customer-facing view of a saga. Hides internal states (STARTED, COMPENSATING, ...):
 * PENDING = still working (incl. cancelling), CONFIRMED = COMPLETED, FAILED = COMPENSATED (+ reason).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderStatus(UUID orderId, Status status, FailureReason reason, String message) {

    public enum Status { PENDING, CONFIRMED, FAILED }

    static OrderStatus from(Saga s) {
        return switch (s.state) {
            case COMPLETED -> new OrderStatus(s.orderId, Status.CONFIRMED, null, "Your order is confirmed.");
            case COMPENSATED -> {
                FailureReason r = s.failureReason == null ? FailureReason.TEMPORARY_ERROR : s.failureReason;
                yield new OrderStatus(s.orderId, Status.FAILED, r, r.message());
            }
            default -> new OrderStatus(s.orderId, Status.PENDING, null, "Your order is being processed.");
        };
    }
}
