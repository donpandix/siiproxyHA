alter table sii_submission
  add column if not exists reconciliation_count int not null default 0,
  add column if not exists failure_class varchar(40),
  add column if not exists outcome_unknown_at timestamptz,
  add column if not exists reconciled_at timestamptz;
