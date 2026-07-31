package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiiSubmissionEnqueueServiceTest {

    @Test
    void createsOneCertificationSubmissionForStoredArtifact() {
        SiiSubmissionRepository repository = mock(SiiSubmissionRepository.class);
        SiiProperties properties = new SiiProperties();
        properties.setEnvironment("CERTIFICATION");
        SiiSubmissionEnqueueService service =
                new SiiSubmissionEnqueueService(repository, properties);
        Dte dte = new Dte();
        dte.setId(UUID.randomUUID());
        DocumentMetadata metadata = metadata();
        SiiSubmissionEntity expected = new SiiSubmissionEntity();

        when(repository.findByDteIdAndEnvironmentAndArtifactSha256(
                dte.getId(),
                "CERTIFICATION",
                metadata.getSha256()
        )).thenReturn(Optional.empty(), Optional.of(expected));

        SiiSubmissionEntity result = service.enqueue(dte, metadata);

        assertSame(expected, result);
        verify(repository).insertPendingIfAbsent(
                any(UUID.class),
                eq(dte.getId()),
                eq(metadata.getDocumentId()),
                eq(metadata.getSigningCredentialId()),
                eq("CERTIFICATION"),
                eq(metadata.getObjectKey()),
                eq(metadata.getSha256()),
                eq(metadata.getSizeBytes()),
                any()
        );
    }

    @Test
    void returnsExistingSubmissionForSameArtifact() {
        SiiSubmissionRepository repository = mock(SiiSubmissionRepository.class);
        SiiProperties properties = new SiiProperties();
        properties.setEnvironment("CERTIFICATION");
        SiiSubmissionEnqueueService service =
                new SiiSubmissionEnqueueService(repository, properties);
        Dte dte = new Dte();
        dte.setId(UUID.randomUUID());
        DocumentMetadata metadata = metadata();
        SiiSubmissionEntity existing = new SiiSubmissionEntity();
        when(repository.findByDteIdAndEnvironmentAndArtifactSha256(
                dte.getId(),
                "CERTIFICATION",
                metadata.getSha256()
        )).thenReturn(Optional.of(existing));

        assertSame(existing, service.enqueue(dte, metadata));
        verify(repository, never()).insertPendingIfAbsent(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any()
        );
    }

    private DocumentMetadata metadata() {
        DocumentMetadata metadata = new DocumentMetadata(
                UUID.randomUUID().toString(),
                DocumentStatus.STORED
        );
        metadata.setObjectKey("dte/test.xml");
        metadata.setSha256("a".repeat(64));
        metadata.setSizeBytes(100L);
        metadata.setSigningCredentialId(UUID.randomUUID());
        return metadata;
    }
}
