create table if not exists processing_history (
  id bigserial primary key,
  document_id varchar(200) not null,
  from_state varchar(50),
  to_state varchar(50) not null,
  actor varchar(100) not null,
  notes varchar(500),
  created_at timestamptz not null default now(),
  constraint fk_processing_history_document
    foreign key (document_id)
    references document_metadata(document_id)
    on delete cascade
);

create index if not exists idx_processing_history_document_created
  on processing_history(document_id, created_at);
