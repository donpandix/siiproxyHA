package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.exception.DocumentRegenerationConflictException;
import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DteXmlRegenerationServiceTest {

    @Mock
    private DteCrudService dteCrudService;
    @Mock
    private DocumentoRepositoryPort documentoRepository;
    @Mock
    private StoragePort storagePort;
    @Mock
    private DteXmlAssemblyService xmlAssembly;

    private DteXmlRegenerationService service;
    private AtomicReference<DocumentMetadata> metadata;

    @BeforeEach
    void setUp() {
        service = new DteXmlRegenerationService(
                dteCrudService,
                documentoRepository,
                storagePort,
                xmlAssembly
        );
        metadata = new AtomicReference<>();
    }

    @Test
    void rebuildsSignsAndReplacesStoredArtifact() throws Exception {
        UUID documentId = UUID.randomUUID();
        Dte dte = dte(documentId);
        DocumentMetadata stored = new DocumentMetadata(
                documentId.toString(),
                DocumentStatus.STORED
        );
        stored.setFolio("182");
        stored.setObjectKey(objectKey(documentId));
        stored.setAttemptCount(1);
        metadata.set(stored);
        byte[] signedXml = (
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                        + "<EnvioDTE xsi:schemaLocation=\""
                        + "http://www.sii.cl/SiiDte EnvioDTE_v10.xsd\"/>"
        ).getBytes(StandardCharsets.ISO_8859_1);

        when(dteCrudService.findForStorage(documentId, null))
                .thenReturn(Optional.of(dte));
        when(documentoRepository.createIfAbsent(any(DocumentMetadata.class)))
                .thenReturn(stored);
        when(documentoRepository.tryClaimRegeneration(
                eq(documentId.toString()),
                any(OffsetDateTime.class)
        )).thenAnswer(invocation -> {
            stored.setStatus(DocumentStatus.PENDING_STORE);
            stored.setAttemptCount(2);
            return true;
        });
        when(documentoRepository.findByDocumentId(documentId.toString()))
                .thenAnswer(invocation -> Optional.of(metadata.get()));
        when(documentoRepository.save(any(DocumentMetadata.class)))
                .thenAnswer(invocation -> {
                    DocumentMetadata saved = invocation.getArgument(0);
                    metadata.set(saved);
                    return saved;
                });
        when(xmlAssembly.build(dte)).thenReturn(new DteXmlBuilderPort.BuiltDteXml(
                signedXml,
                "DTE-182",
                "SetDTE-" + documentId,
                "ISO-8859-1"
        ));
        when(storagePort.store(
                eq(objectKey(documentId)),
                any(InputStream.class),
                anyLong(),
                eq("application/xml")
        )).thenReturn(objectKey(documentId));

        DocumentMetadata result = service.regenerate(documentId.toString());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.STORED);
        assertThat(result.getAttemptCount()).isEqualTo(2);
        assertThat(result.getObjectKey()).isEqualTo(objectKey(documentId));
        assertThat(result.getSizeBytes()).isEqualTo((long) signedXml.length);
        assertThat(result.getSha256()).hasSize(64);
        assertThat(result.getLastError()).isNull();
        verify(xmlAssembly).build(dte);
        verify(storagePort).store(
                eq(objectKey(documentId)),
                any(InputStream.class),
                eq((long) signedXml.length),
                eq("application/xml")
        );
    }

    @Test
    void rejectsMissingDte() {
        UUID documentId = UUID.randomUUID();
        when(dteCrudService.findForStorage(documentId, null))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.regenerate(documentId.toString())
        );
    }

    @Test
    void rejectsConcurrentRegeneration() {
        UUID documentId = UUID.randomUUID();
        Dte dte = dte(documentId);
        DocumentMetadata stored = new DocumentMetadata(
                documentId.toString(),
                DocumentStatus.STORED
        );
        when(dteCrudService.findForStorage(documentId, null))
                .thenReturn(Optional.of(dte));
        when(documentoRepository.createIfAbsent(any(DocumentMetadata.class)))
                .thenReturn(stored);
        when(documentoRepository.tryClaimRegeneration(
                eq(documentId.toString()),
                any(OffsetDateTime.class)
        )).thenReturn(false);

        assertThrows(
                DocumentRegenerationConflictException.class,
                () -> service.regenerate(documentId.toString())
        );
    }

    private Dte dte(UUID documentId) {
        Dte dte = new Dte();
        dte.setId(documentId);
        dte.setFolio(182L);
        dte.setFchEmis(LocalDate.of(2026, 7, 25));
        return dte;
    }

    private String objectKey(UUID documentId) {
        return "dte/2026/07/" + documentId + "-182.xml";
    }
}
