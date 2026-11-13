create table event_timeline (
    id          bigserial primary key,
    event_id    varchar(64)  not null unique,
    order_id    varchar(36)  not null,
    event_type  varchar(50)  not null,
    service     varchar(50)  not null,       -- producer of the event
    trace_id    varchar(32),
    occurred_at timestamptz  not null,
    recorded_at timestamptz  not null default now()
);
create index idx_timeline_order on event_timeline (order_id, occurred_at);

create table processed_event (
    event_id     varchar(64) primary key,
    processed_at timestamptz not null default now()
);

-- the tracker never publishes; this empty table only keeps the shared OutboxPoller (common module) happy
create table outbox (
    id           bigserial primary key,
    topic        varchar(100) not null,
    msg_key      varchar(100) not null,
    event_type   varchar(50)  not null,
    payload      text         not null,
    traceparent  varchar(64),
    created_at   timestamptz  not null default now(),
    published_at timestamptz
);
