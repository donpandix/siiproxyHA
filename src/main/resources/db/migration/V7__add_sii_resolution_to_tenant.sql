-- Resolution data belongs to the registered emitter and is read-only during DTE ingest.
-- Columns are nullable to preserve tenants created before this migration.
alter table tenant
  add column if not exists fch_resol date,
  add column if not exists nro_resol integer;

comment on column tenant.fch_resol is 'Date of the SII resolution used in EnvioDTE';
comment on column tenant.nro_resol is 'Number of the SII resolution used in EnvioDTE';
