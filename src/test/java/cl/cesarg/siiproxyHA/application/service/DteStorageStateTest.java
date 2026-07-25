package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DteStorageStateTest {

    @Test
    void storesOnceWithDeterministicKeyAndReturnsSameArtifactOnReplay() throws Exception {
        byte[] xml = "<EnvioDTE/>".getBytes(StandardCharsets.ISO_8859_1);
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        InMemoryStorage storage = new InMemoryStorage();
        DteXmlAssemblyService assembly = assemblyReturning(xml);
        DteServiceImpl service = service(repository, storage, assembly);
        Dte dte = dte();

        DocumentMetadata first = service.store(dte);
        DocumentMetadata replay = service.store(dte);

        assertSame(first, replay);
        assertEquals(DocumentStatus.STORED, replay.getStatus());
        assertEquals("dte/2026/07/" + dte.getId() + "-182.xml", replay.getObjectKey());
        assertEquals(64, replay.getSha256().length());
        assertEquals((long) xml.length, replay.getSizeBytes());
        assertEquals(1, replay.getAttemptCount());
        assertEquals(1, storage.storeCalls);
        verify(assembly, times(1)).build(dte);
    }

    @Test
    void recoversObjectWrittenBeforeStorageFailureWithoutSigningAgain() throws Exception {
        byte[] xml = "<EnvioDTE id=\"recover\"/>".getBytes(StandardCharsets.ISO_8859_1);
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        InMemoryStorage storage = new InMemoryStorage();
        storage.failAfterWrite = true;
        DteXmlAssemblyService assembly = assemblyReturning(xml);
        DteServiceImpl service = service(repository, storage, assembly);
        Dte dte = dte();

        assertThrows(ObjectStorageException.class, () -> service.store(dte));
        assertEquals(DocumentStatus.FAILED_RECOVERABLE, repository.metadata.getStatus());
        assertEquals("Object storage write failed", repository.metadata.getLastError());

        storage.failAfterWrite = false;
        DocumentMetadata recovered = service.store(dte);

        assertEquals(DocumentStatus.STORED, recovered.getStatus());
        assertEquals(2, recovered.getAttemptCount());
        assertEquals(1, storage.storeCalls);
        verify(assembly, times(1)).build(dte);
    }

    @Test
    void doesNotDuplicateWorkWhileAnotherFreshClaimIsPending() throws Exception {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        Dte dte = dte();
        DocumentMetadata pending = new DocumentMetadata(dte.getId().toString(), DocumentStatus.PENDING_STORE);
        pending.setObjectKey("dte/pending.xml");
        pending.setAttemptCount(1);
        pending.setUpdatedAt(OffsetDateTime.now());
        repository.metadata = pending;
        InMemoryStorage storage = new InMemoryStorage();
        DteXmlAssemblyService assembly = mock(DteXmlAssemblyService.class);

        DocumentMetadata result = service(repository, storage, assembly).store(dte);

        assertSame(pending, result);
        assertEquals(DocumentStatus.PENDING_STORE, result.getStatus());
        assertEquals(0, storage.storeCalls);
        verify(assembly, times(0)).build(dte);
    }

    private DteServiceImpl service(InMemoryDocumentRepository repository,
                                   InMemoryStorage storage,
                                   DteXmlAssemblyService assembly) {
        return new DteServiceImpl(repository, storage, null, null, null, assembly);
    }

    private DteXmlAssemblyService assemblyReturning(byte[] xml) {
        DteXmlAssemblyService assembly = mock(DteXmlAssemblyService.class);
        when(assembly.build(org.mockito.ArgumentMatchers.any(Dte.class))).thenReturn(
                new DteXmlBuilderPort.BuiltDteXml(xml, "Documento-182", "SetDTE-test", "ISO-8859-1")
        );
        return assembly;
    }

    private Dte dte() {
        Dte dte = new Dte();
        dte.setId(UUID.randomUUID());
        dte.setFolio(182L);
        dte.setFchEmis(LocalDate.of(2026, 7, 25));
        return dte;
    }

    private static final class InMemoryStorage implements StoragePort {
        private byte[] bytes;
        private int storeCalls;
        private boolean failAfterWrite;

        @Override
        public String store(String key, InputStream content, long size, String contentType) throws Exception {
            storeCalls++;
            bytes = content.readAllBytes();
            if (failAfterWrite) {
                throw new ObjectStorageException("Ambiguous storage failure", new IllegalStateException("timeout"));
            }
            return key;
        }

        @Override
        public byte[] get(String key) {
            if (bytes == null) {
                throw new ObjectStorageException("Not found", new IllegalStateException("missing"));
            }
            return bytes;
        }

        @Override
        public String presignedUrl(String key, int minutes) {
            return key;
        }
    }

    private static final class InMemoryDocumentRepository implements DocumentoRepositoryPort {
        private DocumentMetadata metadata;

        @Override
        public DocumentMetadata save(DocumentMetadata meta) {
            metadata = meta;
            metadata.setUpdatedAt(OffsetDateTime.now());
            return metadata;
        }

        @Override
        public DocumentMetadata createIfAbsent(DocumentMetadata meta) {
            if (metadata == null) {
                metadata = meta;
            }
            return metadata;
        }

        @Override
        public Optional<DocumentMetadata> findByDocumentId(String documentId) {
            return metadata == null || !documentId.equals(metadata.getDocumentId())
                    ? Optional.empty()
                    : Optional.of(metadata);
        }

        @Override
        public boolean tryClaimStore(String documentId, OffsetDateTime staleBefore) {
            if (metadata == null || metadata.getStatus() == DocumentStatus.STORED
                    || metadata.getStatus() == DocumentStatus.FAILED_FATAL) {
                return false;
            }
            if (metadata.getStatus() == DocumentStatus.PENDING_STORE
                    && metadata.getUpdatedAt() != null
                    && !metadata.getUpdatedAt().isBefore(staleBefore)) {
                return false;
            }
            metadata.setStatus(DocumentStatus.PENDING_STORE);
            metadata.setAttemptCount((metadata.getAttemptCount() == null ? 0 : metadata.getAttemptCount()) + 1);
            metadata.setUpdatedAt(OffsetDateTime.now());
            return true;
        }
    }
}
