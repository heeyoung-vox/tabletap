create table category (
    id         uuid         primary key,
    tenant_id  uuid         not null references tenant (id),
    name       varchar(120) not null,
    sort_order integer      not null default 0,
    created_at timestamptz  not null default now()
);

create index category_tenant_id_idx on category (tenant_id, sort_order);

create table menu_item (
    id          uuid          primary key,
    tenant_id   uuid          not null references tenant (id),
    category_id uuid          not null references category (id),
    name        varchar(120)  not null,
    description varchar(1000),
    base_price  bigint        not null check (base_price >= 0),
    image_url   varchar(500),
    available   boolean       not null default true,
    sort_order  integer       not null default 0,
    created_at  timestamptz   not null default now()
);

create index menu_item_tenant_id_idx on menu_item (tenant_id, category_id, sort_order);

create table option_group (
    id           uuid         primary key,
    tenant_id    uuid         not null references tenant (id),
    menu_item_id uuid         not null references menu_item (id) on delete cascade,
    name         varchar(120) not null,
    min_select   integer      not null,
    max_select   integer      not null,
    sort_order   integer      not null default 0,
    created_at   timestamptz  not null default now(),
    check (min_select >= 0 and max_select >= 1 and max_select >= min_select)
);

create index option_group_tenant_id_idx on option_group (tenant_id, menu_item_id);

-- "option" is an SQL keyword, so the table is named menu_option.
create table menu_option (
    id              uuid         primary key,
    tenant_id       uuid         not null references tenant (id),
    option_group_id uuid         not null references option_group (id) on delete cascade,
    name            varchar(120) not null,
    price_delta     bigint       not null check (price_delta >= 0),
    available       boolean      not null default true,
    sort_order      integer      not null default 0,
    created_at      timestamptz  not null default now()
);

create index menu_option_tenant_id_idx on menu_option (tenant_id, option_group_id);
