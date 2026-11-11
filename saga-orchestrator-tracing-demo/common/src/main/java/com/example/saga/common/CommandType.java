package com.example.saga.common;

public enum CommandType {
    RESERVE_INVENTORY(Topics.INVENTORY_COMMANDS),
    RELEASE_INVENTORY(Topics.INVENTORY_COMMANDS),
    AUTHORIZE_PAYMENT(Topics.PAYMENT_COMMANDS),
    VOID_PAYMENT(Topics.PAYMENT_COMMANDS);

    private final String topic;

    CommandType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
