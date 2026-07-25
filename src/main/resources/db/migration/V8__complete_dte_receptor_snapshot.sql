-- Preserve all receiver data used when a DTE is ingested.
-- Existing snapshot columns already cover RUT, name, address, commune, city and email.
alter table dte
  add column if not exists giro_recep varchar(40),
  add column if not exists telefono_recep varchar(20);

comment on column dte.giro_recep is 'Receiver business activity snapshot at DTE ingest time';
comment on column dte.telefono_recep is 'Receiver phone snapshot at DTE ingest time';
