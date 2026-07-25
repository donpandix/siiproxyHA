package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCertificateRepository extends JpaRepository<UserCertificateEntity, UUID> {
    boolean existsByTenantIdAndCertificateHash(UUID tenantId, String certificateHash);
    List<UserCertificateEntity> findByTenantIdAndStatus(UUID tenantId, String status);
    List<UserCertificateEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<UserCertificateEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
