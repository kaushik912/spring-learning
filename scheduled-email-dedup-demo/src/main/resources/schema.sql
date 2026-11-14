-- ShedLock table (layer 1: only one instance runs the publisher per tick)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Outbox = the work queue. UNIQUE(run_id, user_id) is the real dedup guarantee (layer 2).
CREATE TABLE IF NOT EXISTS email_outbox (
    id         BIGSERIAL PRIMARY KEY,
    run_id     VARCHAR(32) NOT NULL,
    user_id    INT         NOT NULL,
    status     VARCHAR(10) NOT NULL,          -- PENDING | CLAIMED | SENT
    attempts   INT         NOT NULL DEFAULT 0,
    claimed_by VARCHAR(64),
    claimed_at TIMESTAMPTZ,
    sent_at    TIMESTAMPTZ,
    CONSTRAINT uq_outbox_run_user UNIQUE (run_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON email_outbox (status, id);

-- Mock email provider side (same DB for convenience; a real provider is external).
-- PK on idempotency_key = provider-side dedup (layer 4).
CREATE TABLE IF NOT EXISTS provider_delivered (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    run_id          VARCHAR(32) NOT NULL,
    user_id         INT         NOT NULL,
    delivered_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every API call the provider received (to show retries / suppressed duplicates).
CREATE TABLE IF NOT EXISTS provider_calls (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL,
    run_id          VARCHAR(32) NOT NULL,
    outcome         VARCHAR(20) NOT NULL,     -- DELIVERED | DUPLICATE_IGNORED | TRANSIENT_ERROR
    instance        VARCHAR(64) NOT NULL,
    at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
