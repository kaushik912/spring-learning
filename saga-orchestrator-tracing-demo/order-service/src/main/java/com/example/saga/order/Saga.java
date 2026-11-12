package com.example.saga.order;

import com.example.saga.common.CommandType;

import java.time.Instant;
import java.util.UUID;

/** Mutable row of saga_instance. */
public class Saga {
    UUID id;
    UUID orderId;
    String sku;
    int qty;
    long amount;
    SagaState state;
    CommandType currentStep;     // command currently in flight (null when none)
    boolean needsRelease;
    boolean needsVoid;
    int attempts;
    FailureReason failureReason; // set when compensation starts; null while healthy
    Instant nextActionAt;
    String traceId;
    String traceParent;
    String stepTraceParent;
}
