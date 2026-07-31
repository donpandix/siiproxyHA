alter table sii_submission
  add column if not exists informed_count int,
  add column if not exists accepted_count int,
  add column if not exists rejected_count int,
  add column if not exists repair_count int;
