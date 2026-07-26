begin;

-- Protección: cambia 'dte' si tu base tiene otro nombre.
do $$
begin
    if current_database() <> 'dte' then
        raise exception
            'Limpieza cancelada. Base conectada: %, base esperada: dte',
            current_database();
    end if;
end
$$;

truncate table
    public.processing_history,
    public.dte_status_event,
    public.dte_reference,
    public.dte_item,
    public.dte_artifact,
    public.dte_xml,
    public.document_metadata,
    public.folio_assignment,
    public.dte,
    public.folio_pool,
    public.caf,
    public.user_certificate,
    public.receptor,
    public.tenant
restart identity cascade;

commit;

-- revisar que la limpieza fue exitosa
select
    (select count(*) from tenant) as tenants,
    (select count(*) from receptor) as receptores,
    (select count(*) from dte) as dtes,
    (select count(*) from caf) as cafs,
    (select count(*) from user_certificate) as certificados,
    (select count(*) from document_metadata) as documentos;