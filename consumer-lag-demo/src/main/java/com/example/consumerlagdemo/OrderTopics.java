package com.example.consumerlagdemo;

public final class OrderTopics {

    public static final String BUGGY_ORDERS = "buggy-orders";
    public static final String FAST_ORDERS = "fast-orders";
    public static final String SCALED_ORDERS = "scaled-orders";

    public static final String BUGGY_GROUP = "buggy-order-processor";
    public static final String FAST_GROUP = "fast-order-processor";
    public static final String SCALED_GROUP = "scaled-order-processor";

    private OrderTopics() {
    }
}
