alter table document_metadata
  add column if not exists sha256 varchar(64),
  add column if not exists size_bytes bigint,
  add column if not exists attempt_count integer not null default 0,
  add column if not exists last_error varchar(500),
  add column if not exists updated_at timestamptz not null default now();

update document_metadata
   set document_id = 'legacy-' || id::text
 where document_id is null;

update document_metadata
   set updated_at = created_at
 where updated_at is null;

alter table document_metadata
  alter column document_id set not null,
  add constraint ck_document_metadata_attempt_count
  check (attempt_count >= 0);
