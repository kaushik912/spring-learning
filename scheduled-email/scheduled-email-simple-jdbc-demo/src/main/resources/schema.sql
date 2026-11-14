-- ShedLock: one row per lock name
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- One row per (run, user). PRIMARY KEY = "this user is emailed at most once per run".
-- status: PENDING -> SENT | FAILED.  FAILED with attempts >= max = gave up (dead).
CREATE TABLE IF NOT EXISTS emails_sent (
    run_id       VARCHAR(32) NOT NULL,
    user_id      INT         NOT NULL,
    status       VARCHAR(10) NOT NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    resend_count INT         NOT NULL DEFAULT 0,   -- bumped by support "resend"; part of the idempotency key
    last_error   VARCHAR(200),
    resent_by    VARCHAR(64),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, user_id)
);

-- Mock email provider (a real one is external). PK on idempotency_key = provider-side dedup.
CREATE TABLE IF NOT EXISTS provider_delivered (
    idempotency_key VARCHAR(80) PRIMARY KEY,
    user_id         INT         NOT NULL,
    delivered_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS provider_calls (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(80) NOT NULL,
    outcome         VARCHAR(20) NOT NULL,   -- DELIVERED | DUPLICATE_IGNORED | TRANSIENT_ERROR
    at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
