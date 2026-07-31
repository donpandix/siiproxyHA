package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SiiSubmissionEnqueueService {

    private final SiiSubmissionRepository repository;
    private final SiiProperties properties;

    public SiiSubmissionEnqueueService(
            SiiSubmissionRepository repository,
            SiiProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public SiiSubmissionEntity enqueue(Dte dte, DocumentMetadata metadata) {
        if (metadata.getStatus() != DocumentStatus.STORED
                || metadata.getObjectKey() == null
                || metadata.getSha256() == null
                || metadata.getSizeBytes() == null
                || metadata.getSigningCredentialId() == null) {
            throw new IllegalStateException(
                    "Stored signed XML metadata is incomplete for SII submission"
            );
        }
        String environment = properties.normalizedEnvironment();
        var existing = repository.findByDteIdAndEnvironmentAndArtifactSha256(
                dte.getId(),
                environment,
                metadata.getSha256()
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.insertPendingIfAbsent(
                UUID.randomUUID(),
                dte.getId(),
                metadata.getDocumentId(),
                metadata.getSigningCredentialId(),
                environment,
                metadata.getObjectKey(),
                metadata.getSha256(),
                metadata.getSizeBytes(),
                now
        );
        return repository.findByDteIdAndEnvironmentAndArtifactSha256(
                dte.getId(),
                environment,
                metadata.getSha256()
        ).orElseThrow(() -> new IllegalStateException(
                "SII submission could not be created"
        ));
    }
}
