package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiiSubmissionRepository extends JpaRepository<SiiSubmissionEntity, UUID> {

    Optional<SiiSubmissionEntity> findByDteIdAndEnvironmentAndArtifactSha256(
            UUID dteId,
            String environment,
            String artifactSha256
    );

    List<SiiSubmissionEntity> findByDteIdOrderByCreatedAtDesc(UUID dteId);

    @Modifying
    @Query(value = """
            insert into sii_submission (
                id,
                dte_id,
                document_id,
                signing_credential_id,
                environment,
                artifact_key,
                artifact_sha256,
                artifact_size_bytes,
                status,
                attempt_count,
                status_query_count,
                next_attempt_at,
                created_at,
                updated_at
            ) values (
                :id,
                :dteId,
                :documentId,
                :signingCredentialId,
                :environment,
                :artifactKey,
                :artifactSha256,
                :artifactSizeBytes,
                'PENDING_UPLOAD',
                0,
                0,
                :now,
                :now,
                :now
            )
            on conflict (dte_id, environment, artifact_sha256) do nothing
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("dteId") UUID dteId,
            @Param("documentId") String documentId,
            @Param("signingCredentialId") UUID signingCredentialId,
            @Param("environment") String environment,
            @Param("artifactKey") String artifactKey,
            @Param("artifactSha256") String artifactSha256,
            @Param("artifactSizeBytes") Long artifactSizeBytes,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            select id
              from sii_submission
             where (
                    status = 'PENDING_UPLOAD'
                    or (status = 'RECEIVED' and next_attempt_at <= :now)
                    or (
                        status in ('UPLOADING', 'STATUS_QUERYING')
                        and claimed_at < :staleBefore
                    )
             )
               and next_attempt_at <= :now
             order by next_attempt_at, created_at
             limit 1
             for update skip locked
            """, nativeQuery = true)
    Optional<UUID> findNextClaimableId(
            @Param("now") OffsetDateTime now,
            @Param("staleBefore") OffsetDateTime staleBefore
    );
}
