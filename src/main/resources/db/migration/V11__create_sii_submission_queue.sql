alter table document_metadata
  add column if not exists signing_credential_id uuid null
    references user_certificate(id);

create table if not exists sii_submission (
  id uuid primary key,
  dte_id uuid not null references dte(id) on delete cascade,
  document_id varchar(200) not null
    references document_metadata(document_id) on delete cascade,
  signing_credential_id uuid not null references user_certificate(id),
  environment varchar(20) not null,
  artifact_key varchar(500) not null,
  artifact_sha256 char(64) not null,
  artifact_size_bytes bigint not null,
  status varchar(40) not null,
  attempt_count int not null default 0,
  status_query_count int not null default 0,
  track_id bigint,
  sii_status varchar(10),
  sii_glosa varchar(500),
  numero_atencion varchar(40),
  remote_http_status int,
  response_object_key varchar(500),
  response_sha256 char(64),
  last_error varchar(500),
  next_attempt_at timestamptz not null default now(),
  claimed_at timestamptz,
  uploaded_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_sii_submission_artifact
    unique (dte_id, environment, artifact_sha256),
  constraint ck_sii_submission_artifact_size
    check (artifact_size_bytes > 0)
);

create index if not exists idx_sii_submission_work
  on sii_submission (status, next_attempt_at, created_at);

create index if not exists idx_sii_submission_dte
  on sii_submission (dte_id, created_at desc);

create index if not exists idx_sii_submission_track
  on sii_submission (environment, track_id);
