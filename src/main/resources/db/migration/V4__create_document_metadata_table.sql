-- ======================================================================
-- Document metadata table: tracks uploaded documents and storage keys
-- ======================================================================

create table if not exists document_metadata (
  id bigserial primary key,
  document_id varchar(200) unique,
  folio varchar(50),
  status varchar(50),
  object_key varchar(500),
  created_at timestamptz not null default now()
);

create index if not exists idx_document_metadata_document_id on document_metadata(document_id);
