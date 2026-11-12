create table dining_table (
    id         uuid        primary key,
    tenant_id  uuid        not null references tenant (id),
    label      varchar(40) not null,
    qr_token   uuid        not null unique,
    active     boolean     not null default true,
    created_at timestamptz not null default now(),
    unique (tenant_id, label)
);
