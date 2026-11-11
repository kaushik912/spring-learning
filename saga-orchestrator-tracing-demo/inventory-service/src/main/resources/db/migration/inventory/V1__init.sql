create table inventory (
    sku        varchar(64) primary key,
    available  int not null check (available >= 0)
);

create table reservation (
    order_id  uuid primary key,
    sku       varchar(64) not null,
    qty       int         not null,
    status    varchar(16) not null  -- RESERVED | RELEASED
);

-- idempotency: one row per command already handled, with the reply we gave (replayed on duplicates)
create table processed_command (
    command_id    varchar(128) primary key,
    reply_type    varchar(32)  not null,
    reason        varchar(255),
    processed_at  timestamptz  not null default now()
);

insert into inventory (sku, available) values ('SKU-1', 10), ('SKU-2', 0);
