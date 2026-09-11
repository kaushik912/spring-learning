package com.example.sagatransferdemo.saga;

public final class SagaTopics {

    public static final String DEBIT_REQUESTED = "debit-requested";
    public static final String DEBIT_RESULT = "debit-result";
    public static final String CREDIT_REQUESTED = "credit-requested";
    public static final String CREDIT_RESULT = "credit-result";
    public static final String REFUND_REQUESTED = "refund-requested";
    public static final String REFUND_RESULT = "refund-result";

    private SagaTopics() {
    }
}
