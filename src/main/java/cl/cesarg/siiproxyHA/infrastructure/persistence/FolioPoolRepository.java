package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.FolioPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolioPoolRepository extends JpaRepository<FolioPool, UUID> {

    @Query(value = """
            select *
            from folio_pool fp
            where fp.tenant_id = :tenantId
              and fp.tipo_dte = :tipoDte
              and fp.punto_venta = :puntoVenta
              and fp.status = 'ACTIVE'
            order by fp.folio_desde asc
            limit 1
            for update
            """, nativeQuery = true)
    Optional<FolioPool> lockFirstActivePool(@Param("tenantId") UUID tenantId,
                                            @Param("tipoDte") Integer tipoDte,
                                            @Param("puntoVenta") Integer puntoVenta);

    List<FolioPool> findByTenantIdAndTipoDteAndPuntoVentaOrderByFolioDesdeAsc(UUID tenantId,
                                                                               Integer tipoDte,
                                                                               Integer puntoVenta);
}
