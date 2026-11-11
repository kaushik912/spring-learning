create table payment (
    order_id  uuid primary key,
    amount    bigint      not null,
    status    varchar(16) not null  -- AUTHORIZED | VOIDED
);

-- idempotency: one row per command already handled, with the reply we gave (replayed on duplicates)
create table processed_command (
    command_id    varchar(128) primary key,
    reply_type    varchar(32)  not null,
    reason        varchar(255),
    processed_at  timestamptz  not null default now()
);
