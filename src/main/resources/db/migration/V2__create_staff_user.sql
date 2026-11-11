create table staff_user (
    id            uuid         primary key,
    tenant_id     uuid         not null references tenant (id),
    email         varchar(255) not null unique,
    password_hash varchar(100) not null,
    role          varchar(20)  not null check (role in ('OWNER', 'STAFF')),
    created_at    timestamptz  not null default now()
);

create index staff_user_tenant_id_idx on staff_user (tenant_id);
