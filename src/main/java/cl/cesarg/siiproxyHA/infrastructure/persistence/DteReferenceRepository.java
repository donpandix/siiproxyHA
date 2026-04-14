package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.DteReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DteReferenceRepository extends JpaRepository<DteReference, UUID> {
}
