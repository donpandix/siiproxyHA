package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.DteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DteItemRepository extends JpaRepository<DteItem, UUID> {
}
