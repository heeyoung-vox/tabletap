create table tenant (
    id         uuid         primary key,
    name       varchar(120) not null,
    slug       varchar(60)  not null unique,
    currency   varchar(3)   not null default 'VND',
    created_at timestamptz  not null default now()
);
