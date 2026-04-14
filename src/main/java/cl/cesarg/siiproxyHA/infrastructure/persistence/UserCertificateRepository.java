package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserCertificateRepository extends JpaRepository<UserCertificateEntity, UUID> {
    boolean existsByTenantIdAndCertificateHash(UUID tenantId, String certificateHash);
}
