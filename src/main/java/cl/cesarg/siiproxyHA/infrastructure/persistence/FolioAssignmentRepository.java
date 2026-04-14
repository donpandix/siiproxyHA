package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolioAssignmentRepository extends JpaRepository<FolioAssignment, UUID> {

    Optional<FolioAssignment> findByTenantIdAndRequestId(UUID tenantId, String requestId);

    Optional<FolioAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FolioAssignment> findByTenantIdAndTipoDteAndPuntoVentaOrderByAssignedAtDesc(UUID tenantId,
                                                                                      Integer tipoDte,
                                                                                      Integer puntoVenta);
}
