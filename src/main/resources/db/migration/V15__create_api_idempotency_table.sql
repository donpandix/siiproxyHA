create table if not exists api_idempotency (
  id bigserial primary key,
  tenant_id uuid not null,
  idempotency_key varchar(255) not null,
  operation varchar(64) not null,
  request_hash varchar(128) not null,
  document_id varchar(255) not null,
  created_at timestamptz not null default now(),
  constraint uk_api_idempotency_tenant_key_operation unique (tenant_id, idempotency_key, operation)
);

create index if not exists idx_api_idempotency_tenant_key on api_idempotency(tenant_id, idempotency_key, operation);
