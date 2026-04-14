-- Support point-of-sale aware folio allocation and state tracking.
alter table caf
    add column if not exists punto_venta integer not null default 1;

alter table folio_pool
    add column if not exists punto_venta integer not null default 1;

alter table folio_assignment
    add column if not exists punto_venta integer not null default 1;

create index if not exists idx_caf_tenant_tipo_pv
    on caf (tenant_id, tipo_dte, punto_venta, is_active);

create index if not exists idx_folio_pool_tenant_tipo_pv_status
    on folio_pool (tenant_id, tipo_dte, punto_venta, status, next_folio);

create index if not exists idx_folio_assignment_tenant_tipo_pv_status
    on folio_assignment (tenant_id, tipo_dte, punto_venta, status, assigned_at desc);

create unique index if not exists uq_folio_assignment_tenant_request
    on folio_assignment (tenant_id, request_id)
    where request_id is not null;

-- Backfill point-of-sale value for legacy rows.
update caf set punto_venta = 1 where punto_venta is null;
update folio_pool set punto_venta = 1 where punto_venta is null;
update folio_assignment set punto_venta = 1 where punto_venta is null;

-- Ensure a folio pool exists for historical CAF rows.
insert into folio_pool (
    id,
    tenant_id,
    tipo_dte,
    punto_venta,
    caf_id,
    folio_desde,
    folio_hasta,
    next_folio,
    status,
    created_at,
    updated_at
)
select
    c.id,
    c.tenant_id,
    c.tipo_dte,
    c.punto_venta,
    c.id,
    c.folio_desde,
    c.folio_hasta,
    c.folio_desde,
    case when c.folio_desde <= c.folio_hasta then 'ACTIVE' else 'EXHAUSTED' end,
    coalesce(c.created_at, now()),
    now()
from caf c
where not exists (
    select 1 from folio_pool fp where fp.caf_id = c.id
);
