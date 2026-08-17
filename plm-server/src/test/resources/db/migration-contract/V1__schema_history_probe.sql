create table if not exists plm.flyway_contract_probe (
    probe_id integer primary key,
    applied_at timestamp not null default current_timestamp
);
