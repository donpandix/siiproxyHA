-- ======================================================================
-- User Certificate Management
-- Almacenamiento seguro de certificados digitales y contraseñas cifradas
-- ======================================================================

create table if not exists user_certificate (
  id uuid primary key,
  tenant_id uuid not null references tenant(id) on delete cascade,
  
  -- Identificación del usuario
  rut_usuario varchar(12) not null,
  nombre_usuario varchar(100),
  
  -- Referencia al certificado físico en MinIO/S3
  certificate_path varchar(500) not null,
  certificate_hash varchar(64) not null,  -- SHA-256 del certificado para detectar duplicados
  
  -- Contraseña cifrada con AES-256-GCM
  encrypted_password text not null,
  encryption_iv varchar(64) not null,     -- Initialization Vector para GCM
  encryption_algorithm varchar(50) not null default 'AES/GCM/NoPadding',
  
  -- Metadatos extraídos del certificado
  cert_serial_number varchar(100),
  cert_issuer varchar(500),
  cert_subject varchar(500),
  cert_subject_rut varchar(12),           -- RUT extraído del subject
  valid_from timestamptz,
  valid_until timestamptz,
  
  -- Estado y control
  status varchar(20) not null default 'ACTIVE',  -- ACTIVE, EXPIRED, REVOKED, DISABLED
  is_default boolean not null default false,     -- Certificado por defecto del usuario
  
  -- Auditoría
  created_at timestamptz not null default now(),
  updated_at timestamptz,
  created_by varchar(100),
  last_used_at timestamptz,
  usage_count int not null default 0,
  
  constraint uk_tenant_rut_hash unique (tenant_id, rut_usuario, certificate_hash),
  constraint chk_cert_status check (status in ('ACTIVE', 'EXPIRED', 'REVOKED', 'DISABLED'))
);

-- Índices para optimizar consultas
create index if not exists idx_user_cert_tenant on user_certificate(tenant_id);
create index if not exists idx_user_cert_tenant_rut on user_certificate(tenant_id, rut_usuario);
create index if not exists idx_user_cert_status on user_certificate(status);
create index if not exists idx_user_cert_valid_until on user_certificate(valid_until);
create index if not exists idx_user_cert_default on user_certificate(tenant_id, rut_usuario, is_default) 
  where is_default = true;

-- Trigger para asegurar solo un certificado por defecto por usuario
create or replace function ensure_single_default_certificate()
returns trigger as $$
begin
  if new.is_default = true then
    update user_certificate
    set is_default = false
    where tenant_id = new.tenant_id
      and rut_usuario = new.rut_usuario
      and id != new.id
      and is_default = true;
  end if;
  return new;
end;
$$ language plpgsql;

create trigger trg_ensure_single_default_cert
  before insert or update on user_certificate
  for each row
  execute function ensure_single_default_certificate();

-- Comentarios para documentación
comment on table user_certificate is 'Certificados digitales de usuarios con contraseñas cifradas';
comment on column user_certificate.certificate_hash is 'SHA-256 hash del certificado para detectar duplicados';
comment on column user_certificate.encrypted_password is 'Contraseña cifrada con AES-256-GCM';
comment on column user_certificate.encryption_iv is 'Initialization Vector único para cada cifrado';
comment on column user_certificate.is_default is 'Solo puede haber un certificado por defecto por usuario';
