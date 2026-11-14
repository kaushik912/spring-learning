package com.example.emailsimple;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * One row per (run, user). Implements Persistable because the id is assigned by us, not generated:
 * without it save() would do a "merge" (SELECT, then UPDATE if found), which could reset an existing SENT row
 * back to PENDING in a race. With isNew()=true, save() does a plain INSERT and the primary key rejects duplicates.
 */
@Entity
@Table(name = "emails_sent")
public class EmailRecord implements Persistable<EmailKey> {

    @EmbeddedId
    private EmailKey id;

    @Enumerated(EnumType.STRING)
    private EmailStatus status;

    private int attempts;
    private int resendCount;     // bumped by support "resend"; part of the idempotency key
    private String lastError;
    private String resentBy;
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected EmailRecord() {    // required by JPA
    }

    public EmailRecord(EmailKey id) {
        this.id = id;
        this.status = EmailStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    @Override
    public EmailKey getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getResendCount() {
        return resendCount;
    }
}
