create table orders (
    id              uuid         primary key,
    tenant_id       uuid         not null references tenant (id),
    -- Kept when the table is deleted, so past orders still show where they came from.
    dining_table_id uuid         references dining_table (id) on delete set null,
    table_label     varchar(40)  not null,
    public_token    uuid         not null unique,
    status          varchar(20)  not null
        check (status in ('PLACED', 'ACCEPTED', 'PREPARING', 'SERVED', 'PAID', 'CANCELLED')),
    total           bigint       not null check (total >= 0),
    note            varchar(500),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create index orders_tenant_id_idx on orders (tenant_id, created_at);

create table order_item (
    id           uuid         primary key,
    tenant_id    uuid         not null references tenant (id),
    order_id     uuid         not null references orders (id) on delete cascade,
    position     integer      not null,
    menu_item_id uuid         references menu_item (id) on delete set null,
    item_name    varchar(120) not null,
    unit_price   bigint       not null check (unit_price >= 0),
    quantity     integer      not null check (quantity > 0),
    line_total   bigint       not null check (line_total >= 0),
    note         varchar(200),
    created_at   timestamptz  not null default now()
);

create index order_item_tenant_id_idx on order_item (tenant_id, order_id);

create table order_item_option (
    id            uuid         primary key,
    tenant_id     uuid         not null references tenant (id),
    order_item_id uuid         not null references order_item (id) on delete cascade,
    position      integer      not null,
    group_name    varchar(120) not null,
    option_name   varchar(120) not null,
    price_delta   bigint       not null check (price_delta >= 0),
    created_at    timestamptz  not null default now()
);

create index order_item_option_tenant_id_idx on order_item_option (tenant_id, order_item_id);
