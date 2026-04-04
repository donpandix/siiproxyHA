-- ======================================================================
-- SII Proxy MS - Esquema DB (PostgreSQL)
-- Multi-tenant + Receptores + DTE + Folios/CAF (simple y eficiente)
-- Base package: cl.cesarg.siiproxyms (solo referencia documental)
-- ======================================================================

-- Recomendado (por si quieres gen_random_uuid()):
-- create extension if not exists pgcrypto;

-- ======================================================================
-- 1) TENANT
-- ======================================================================
create table if not exists tenant (
  id uuid primary key,
  tenant_code varchar(50) not null unique,   -- "cliente-1", "acme", etc.
  rut_emisor varchar(12) not null,           -- 76184688-4
  razon_social varchar(100) not null,
  giro varchar(80),
  acteco varchar(6),
  created_at timestamptz not null default now(),
  is_active boolean not null default true
);

create index if not exists idx_tenant_rut_emisor
  on tenant (rut_emisor);

-- ======================================================================
-- 2) RECEPTORES (catálogo por tenant)
-- ======================================================================
create table if not exists receptor (
  id uuid primary key,
  tenant_id uuid not null references tenant(id),

  rut_receptor varchar(12),                  -- puede ser null (extranjero/exportación)
  razon_social varchar(100) not null,
  giro varchar(40),
  email varchar(80),
  telefono varchar(20),

  dir_recep varchar(70),
  cmna_recep varchar(20),
  ciudad_recep varchar(20),

  created_at timestamptz not null default now(),

  constraint uq_receptor_tenant_rut unique (tenant_id, rut_receptor)
);

create index if not exists idx_receptor_tenant
  on receptor (tenant_id);

create index if not exists idx_receptor_tenant_razon
  on receptor (tenant_id, razon_social);

-- ======================================================================
-- 3) CAF (autorización de folios por tenant + tipo_dte)
-- ======================================================================
create table if not exists caf (
  id uuid primary key,
  tenant_id uuid not null references tenant(id),

  tipo_dte int not null,                     -- 33, 39, 52, 61, 56, etc.
  folio_desde bigint not null,
  folio_hasta bigint not null,

  caf_path varchar(500) not null,            -- MinIO storage key
  caf_sha256 char(64),

  rut_emisor varchar(12),
  fch_autorizacion date,

  created_at timestamptz not null default now(),
  is_active boolean not null default true,

  constraint uq_caf_tenant_tipo_rango unique (tenant_id, tipo_dte, folio_desde, folio_hasta),
  constraint ck_caf_rango check (folio_desde <= folio_hasta)
);

create index if not exists idx_caf_tenant_tipo
  on caf (tenant_id, tipo_dte);

-- ======================================================================
-- 4) FOLIO POOL (rango consumible / fila "caliente" por tenant+tipo)
-- ======================================================================
create table if not exists folio_pool (
  id uuid primary key,
  tenant_id uuid not null references tenant(id),
  tipo_dte int not null,
  caf_id uuid not null references caf(id),

  folio_desde bigint not null,
  folio_hasta bigint not null,

  next_folio bigint not null,                -- siguiente a asignar (puede llegar a folio_hasta+1)
  status varchar(20) not null default 'ACTIVE', -- ACTIVE | EXHAUSTED | DISABLED

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint ck_pool_rango check (folio_desde <= folio_hasta),
  constraint ck_pool_next check (next_folio between folio_desde and (folio_hasta + 1))
);

create index if not exists idx_folio_pool_tenant_tipo_status
  on folio_pool (tenant_id, tipo_dte, status);

create index if not exists idx_folio_pool_caf
  on folio_pool (caf_id);

-- ======================================================================
-- 5) DTE (cabecera + snapshot receptor + tracking SII)
-- ======================================================================
create table if not exists dte (
  id uuid primary key,
  tenant_id uuid not null references tenant(id),

  tipo_dte int not null,
  folio bigint not null,
  fch_emis date not null,

  receptor_id uuid null references receptor(id),

  -- Snapshot receptor (histórico inmutable del documento)
  rut_recep varchar(12),
  rzn_soc_recep varchar(100) not null,
  dir_recep varchar(70),
  cmna_recep varchar(20),
  ciudad_recep varchar(20),
  correo_recep varchar(80),

  -- Totales mínimos útiles
  mnt_neto bigint,
  mnt_exe bigint,
  tasa_iva numeric(5,2),
  iva bigint,
  mnt_total bigint not null,

  -- Ciclo SII / tracking
  sii_track_id bigint,
  sii_estado varchar(10),
  sii_glosa varchar(255),
  enviado_at timestamptz,
  last_status_at timestamptz,

  -- Control
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint uq_dte_tenant_tipo_folio unique (tenant_id, tipo_dte, folio)
);

create index if not exists idx_dte_tenant_fecha
  on dte (tenant_id, fch_emis desc);

create index if not exists idx_dte_tenant_track
  on dte (tenant_id, sii_track_id);

create index if not exists idx_dte_tenant_estado
  on dte (tenant_id, sii_estado);

create index if not exists idx_dte_tenant_receptor_rut
  on dte (tenant_id, rut_recep);

-- ======================================================================
-- 6) FOLIO ASSIGNMENT (auditoría / idempotencia / vínculo a DTE)
-- ======================================================================
create table if not exists folio_assignment (
  id uuid primary key,
  tenant_id uuid not null references tenant(id),
  tipo_dte int not null,

  folio bigint not null,
  folio_pool_id uuid not null references folio_pool(id),

  request_id varchar(100),                  -- idempotency key (recomendado)
  assigned_to varchar(50) default 'SYSTEM',
  assigned_at timestamptz not null default now(),

  dte_id uuid null references dte(id),

  status varchar(20) not null default 'ASSIGNED', -- ASSIGNED | USED | VOIDED
  note varchar(255),

  constraint uq_folio_assignment unique (tenant_id, tipo_dte, folio)
);

create index if not exists idx_folio_assign_tenant_tipo
  on folio_assignment (tenant_id, tipo_dte, assigned_at desc);

create index if not exists idx_folio_assign_dte
  on folio_assignment (dte_id);

create index if not exists idx_folio_assign_request
  on folio_assignment (tenant_id, request_id);

-- Amarre opcional explícito desde DTE hacia el assignment (útil)
alter table dte
  add column if not exists folio_assignment_id uuid null references folio_assignment(id);

create index if not exists idx_dte_folio_assignment
  on dte (folio_assignment_id);

-- ======================================================================
-- 7) XML / artifacts
-- Elige UNA de las dos opciones (A o B). Puedes dejar ambas para el MVP,
-- pero luego te conviene decidir para no duplicar responsabilidades.
-- ======================================================================

-- Opción A: Guardar XML dentro de DB (MVP simple)
create table if not exists dte_xml (
  dte_id uuid primary key references dte(id) on delete cascade,
  xml_documento text not null,              -- DTE o EnvioDTE
  xml_envio text,                           -- opcional: sobre completo
  sha256 char(64),
  created_at timestamptz not null default now()
);

-- Opción B: Guardar artifacts en S3/MinIO y solo referenciar (más escalable)
create table if not exists dte_artifact (
  id uuid primary key,
  dte_id uuid not null references dte(id) on delete cascade,
  kind varchar(30) not null,                -- XML_DOCUMENTO | XML_ENVIO | PDF | ACK | ETC
  storage_key varchar(500) not null,        -- key/ruta en S3/MinIO
  sha256 char(64),
  size_bytes bigint,
  created_at timestamptz not null default now()
);

create index if not exists idx_dte_artifact_dte
  on dte_artifact (dte_id);

-- ======================================================================
-- 8) DETALLE DTE
-- ======================================================================
create table if not exists dte_item (
  id uuid primary key,
  dte_id uuid not null references dte(id) on delete cascade,

  nro_lin_det int not null,                 -- 1..60
  nmb_item varchar(80) not null,
  dsc_item varchar(1000),
  qty_item numeric(18,6),
  unmd_item varchar(4),
  prc_item numeric(18,6),
  monto_item bigint,
  ind_exe int,

  created_at timestamptz not null default now(),

  constraint uq_dte_item_line unique (dte_id, nro_lin_det)
);

create index if not exists idx_dte_item_dte
  on dte_item (dte_id);

-- ======================================================================
-- 9) REFERENCIAS (NC/ND/Guías/etc.)
-- ======================================================================
create table if not exists dte_reference (
  id uuid primary key,
  dte_id uuid not null references dte(id) on delete cascade,

  nro_lin_ref int not null,
  tpo_doc_ref varchar(3) not null,          -- 33/39/52/...
  folio_ref varchar(18),
  fch_ref date,
  cod_ref varchar(2),
  razon_ref varchar(90),

  created_at timestamptz not null default now(),

  constraint uq_dte_ref_line unique (dte_id, nro_lin_ref)
);

create index if not exists idx_dte_ref_dte
  on dte_reference (dte_id);

-- ======================================================================
-- 10) HISTORIAL / EVENTOS SII (auditoría)
-- ======================================================================
create table if not exists dte_status_event (
  id uuid primary key,
  dte_id uuid not null references dte(id) on delete cascade,

  event_type varchar(40) not null,          -- SEND_OK | SEND_FAIL | SII_RESPONSE | STATUS_UPDATE
  sii_code varchar(10),
  message varchar(500),
  raw_payload text,

  created_at timestamptz not null default now()
);

create index if not exists idx_dte_status_event_dte
  on dte_status_event (dte_id, created_at desc);

-- ======================================================================
-- FIN
-- ======================================================================
