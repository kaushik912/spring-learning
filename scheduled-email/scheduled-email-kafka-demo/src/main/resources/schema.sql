-- ShedLock: one row per lock name
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- "Run X has been fully published to Kafka" marker. Written AFTER publishing, so a publisher that
-- crashes halfway leaves no marker and the next tick simply publishes again (consumers dedupe).
CREATE TABLE IF NOT EXISTS email_runs (
    run_id       VARCHAR(32) PRIMARY KEY,
    published_by VARCHAR(64) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Outcome of each email request. Key = (run, user, resend_count) = the provider idempotency key.
--   SENT = provider accepted it.   DEAD = retries exhausted, message went to the dead-letter topic.
-- A support "resend" is a new message with resend_count + 1, i.e. a new row.
CREATE TABLE IF NOT EXISTS email_results (
    run_id       VARCHAR(32) NOT NULL,
    user_id      INT         NOT NULL,
    resend_count INT         NOT NULL,
    status       VARCHAR(10) NOT NULL,
    instance     VARCHAR(64),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, user_id, resend_count)
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
