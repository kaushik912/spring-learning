package com.example.saga.order;

/** Why a saga was cancelled, in customer terms. Set once, when compensation starts. */
public enum FailureReason {
    OUT_OF_STOCK("Sorry, this item is out of stock. You were not charged."),
    PAYMENT_DECLINED("Your payment was declined. The reserved items were released and you were not charged."),
    TEMPORARY_ERROR("We could not process your order due to a temporary problem. Please try again. "
            + "Any payment hold is being reversed.");

    private final String message;

    FailureReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
