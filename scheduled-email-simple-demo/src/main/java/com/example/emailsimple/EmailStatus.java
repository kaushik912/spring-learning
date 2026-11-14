package com.example.emailsimple;

/** PENDING -> SENT | FAILED. FAILED with attempts >= max-attempts means "gave up". */
public enum EmailStatus {
    PENDING, SENT, FAILED
}
