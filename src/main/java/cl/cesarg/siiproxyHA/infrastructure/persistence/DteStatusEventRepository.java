package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.DteStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DteStatusEventRepository extends JpaRepository<DteStatusEvent, UUID> {
}
