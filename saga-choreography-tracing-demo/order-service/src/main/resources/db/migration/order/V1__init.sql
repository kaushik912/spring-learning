create table orders (
    id             varchar(36) primary key,
    sku            varchar(50)    not null,
    qty            int            not null,
    amount         numeric(12, 2) not null,
    status         varchar(20)    not null,   -- PENDING | CONFIRMED | REJECTED | CANCELLED
    failure_reason varchar(100),
    created_at     timestamptz    not null default now(),
    updated_at     timestamptz    not null default now()
);

-- transactional outbox: written in the same txn as the state change, published to Kafka by OutboxPoller
create table outbox (
    id           bigserial primary key,
    topic        varchar(100) not null,
    msg_key      varchar(100) not null,
    event_type   varchar(50)  not null,
    payload      text         not null,
    traceparent  varchar(64),              -- W3C trace context of the span that wrote the row
    created_at   timestamptz  not null default now(),
    published_at timestamptz
);
create index idx_outbox_unpublished on outbox (id) where published_at is null;

-- idempotency: one row per handled eventId
create table processed_event (
    event_id     varchar(64) primary key,
    processed_at timestamptz not null default now()
);
