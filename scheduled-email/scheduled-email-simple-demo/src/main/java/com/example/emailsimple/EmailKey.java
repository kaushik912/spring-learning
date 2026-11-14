package com.example.emailsimple;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/** Composite primary key of emails_sent: one row per user per run. */
@Embeddable
public record EmailKey(String runId, int userId) implements Serializable {
}
