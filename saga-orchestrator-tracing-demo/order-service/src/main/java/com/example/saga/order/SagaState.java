package com.example.saga.order;

public enum SagaState {
    STARTED,
    INVENTORY_RESERVED,
    PAYMENT_AUTHORIZED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED
}
