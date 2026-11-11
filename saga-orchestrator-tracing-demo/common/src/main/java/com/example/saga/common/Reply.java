package com.example.saga.common;

/** Participant -> orchestrator. commandId echoes the command being answered. */
public record Reply(String commandId, String orderId, ReplyType type, String reason) {
}
