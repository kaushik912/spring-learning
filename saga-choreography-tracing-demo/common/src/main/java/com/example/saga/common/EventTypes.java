package com.example.saga.common;

import java.util.List;

public final class EventTypes {
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_REJECTED = "OrderRejected";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_REJECTED = "InventoryRejected";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String PAYMENT_AUTHORIZED = "PaymentAuthorized";
    public static final String PAYMENT_FAILED = "PaymentFailed";

    public static final List<String> ALL = List.of(ORDER_CREATED, ORDER_CONFIRMED, ORDER_REJECTED, ORDER_CANCELLED,
            INVENTORY_RESERVED, INVENTORY_REJECTED, INVENTORY_RELEASED, PAYMENT_AUTHORIZED, PAYMENT_FAILED);

    /** Order-service publishes one of these when the order reaches its final status. */
    public static final List<String> TERMINAL = List.of(ORDER_CONFIRMED, ORDER_REJECTED, ORDER_CANCELLED);

    private EventTypes() {
    }
}
