package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.Receptor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReceptorRepository extends JpaRepository<Receptor, UUID> {
	List<Receptor> findByTenantId(UUID tenantId);
	boolean existsByTenantIdAndRutReceptor(UUID tenantId, String rutReceptor);
}
