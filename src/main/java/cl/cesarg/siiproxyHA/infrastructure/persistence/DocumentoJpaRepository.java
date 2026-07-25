package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface DocumentoJpaRepository extends JpaRepository<DocumentoEntity, Long> {
    Optional<DocumentoEntity> findByDocumentId(String documentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DocumentoEntity document
               set document.status = 'PENDING_STORE',
                   document.attemptCount = coalesce(document.attemptCount, 0) + 1,
                   document.lastError = null,
                   document.updatedAt = :now
             where document.documentId = :documentId
               and (
                    document.status in ('RECEIVED', 'FAILED_RECOVERABLE')
                    or (document.status = 'PENDING_STORE' and document.updatedAt < :staleBefore)
               )
            """)
    int claimStore(@Param("documentId") String documentId,
                   @Param("now") OffsetDateTime now,
                   @Param("staleBefore") OffsetDateTime staleBefore);
}
