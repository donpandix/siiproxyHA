-- RutEnvia identifies the authorized certificate holder that prepared the EnvioDTE.
-- Existing DTE rows remain valid without a value; new ingests require it at the API layer.
alter table dte
  add column if not exists rut_envia varchar(12);

create index if not exists idx_dte_tenant_rut_envia
  on dte (tenant_id, rut_envia);

comment on column dte.rut_envia is
  'Snapshot of the authorized sender RUT used when the DTE was ingested';
