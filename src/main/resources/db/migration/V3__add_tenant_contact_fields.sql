-- ======================================================================
-- V3 - Agrega campos de contacto al tenant
-- ======================================================================
alter table tenant
  add column if not exists dir_tenant varchar(70),
  add column if not exists cmna_tenant varchar(20),
  add column if not exists ciudad_tenant varchar(20),
  add column if not exists email varchar(80),
  add column if not exists telefono varchar(20);

-- Opcional: llenar con valores por defecto si es necesario
-- update tenant set email = '' where email is null;
