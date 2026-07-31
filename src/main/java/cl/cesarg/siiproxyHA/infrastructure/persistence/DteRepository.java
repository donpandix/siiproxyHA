package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.UUID;

public interface DteRepository extends JpaRepository<Dte, UUID> {
	java.util.List<Dte> findByTenantId(java.util.UUID tenantId);

	java.util.Optional<Dte> findByIdAndTenantId(java.util.UUID id, java.util.UUID tenantId);

    @EntityGraph(attributePaths = {"tenant"})
    java.util.Optional<Dte> findWithTenantById(java.util.UUID id);
}
