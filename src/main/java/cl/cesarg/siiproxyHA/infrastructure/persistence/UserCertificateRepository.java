package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCertificateRepository extends JpaRepository<UserCertificateEntity, UUID> {
    boolean existsByTenantIdAndCertificateHash(UUID tenantId, String certificateHash);
    List<UserCertificateEntity> findByTenantIdAndStatus(UUID tenantId, String status);
    List<UserCertificateEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<UserCertificateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserCertificateEntity certificate
               set certificate.lastUsedAt = :usedAt,
                   certificate.usageCount = coalesce(certificate.usageCount, 0) + 1
             where certificate.id = :credentialId
               and certificate.status = 'ACTIVE'
            """)
    int recordSuccessfulUse(
            @Param("credentialId") UUID credentialId,
            @Param("usedAt") OffsetDateTime usedAt
    );
}
