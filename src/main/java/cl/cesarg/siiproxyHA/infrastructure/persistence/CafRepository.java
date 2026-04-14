package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CafRepository extends JpaRepository<Caf, UUID> {
	List<Caf> findByTenantIdAndTipoDteAndPuntoVentaAndActiveTrueOrderByCreatedAtAsc(UUID tenantId,
																					 Integer tipoDte,
																					 Integer puntoVenta);
}
