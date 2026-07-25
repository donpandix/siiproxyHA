package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.Receptor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceptorRepository extends JpaRepository<Receptor, UUID> {
	List<Receptor> findByTenantId(UUID tenantId);
	Optional<Receptor> findByTenantIdAndRutReceptor(UUID tenantId, String rutReceptor);
	boolean existsByTenantIdAndRutReceptor(UUID tenantId, String rutReceptor);
}
