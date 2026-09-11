package com.example.sagatransferdemo.saga;

public enum SagaStatus {
    STARTED,
    DEBITED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
