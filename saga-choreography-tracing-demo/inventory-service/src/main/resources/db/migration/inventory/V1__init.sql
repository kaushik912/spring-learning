create table stock (
    sku varchar(50) primary key,
    qty int not null check (qty >= 0)
);
insert into stock(sku, qty) values ('SKU-1', 10), ('SKU-2', 0);

create table reservation (
    order_id varchar(36) primary key,
    sku      varchar(50) not null,
    qty      int         not null,
    status   varchar(20) not null           -- RESERVED | RELEASED
);

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
create index idx_outbox_unpublished on outbox (id) where published_at is null;

create table processed_event (
    event_id     varchar(64) primary key,
    processed_at timestamptz not null default now()
);
