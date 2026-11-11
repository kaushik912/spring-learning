create table saga_instance (
    id               uuid primary key,
    order_id         uuid         not null unique,
    sku              varchar(64)  not null,
    qty              int          not null,
    amount           bigint       not null,
    state            varchar(32)  not null,
    current_step     varchar(32),
    needs_release    boolean      not null default false,  -- compensation still owed: release inventory
    needs_void       boolean      not null default false,  -- compensation still owed: void payment
    attempts         int          not null default 0,      -- compensation retries so far
    next_action_at   timestamptz,                          -- step timeout / next retry; null = nothing pending
    trace_id         varchar(32),
    trace_parent     varchar(64),                          -- context of saga start (parent for timeout/retry spans)
    step_trace_parent varchar(64),                         -- span that sent the in-flight command (span link target)
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now()
);

create index idx_saga_due on saga_instance (next_action_at) where next_action_at is not null;

create table saga_step_log (
    id          bigserial primary key,
    saga_id     uuid        not null references saga_instance (id),
    from_state  varchar(32),
    to_state    varchar(32) not null,
    step        varchar(32),
    detail      varchar(255),
    trace_id    varchar(32),
    created_at  timestamptz not null default now()
);

create index idx_step_log_saga on saga_step_log (saga_id, id);
