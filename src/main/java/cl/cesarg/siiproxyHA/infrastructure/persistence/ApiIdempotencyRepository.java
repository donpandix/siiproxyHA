package cl.cesarg.siiproxyHA.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.cesarg.siiproxyHA.domain.model.ApiIdempotencyEntity;

public interface ApiIdempotencyRepository extends JpaRepository<ApiIdempotencyEntity, Long> {
    Optional<ApiIdempotencyEntity> findByTenantIdAndIdempotencyKeyAndOperation(UUID tenantId, String idempotencyKey, String operation);
}
