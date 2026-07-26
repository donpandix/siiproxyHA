package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.exception.DocumentRegenerationConflictException;
import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Rebuilds, signs and atomically republishes the stored XML for an existing DTE.
 */
@Service
public class DteXmlRegenerationService {

    private static final Duration REGENERATION_CLAIM_LEASE = Duration.ofMinutes(5);

    private final DteCrudService dteCrudService;
    private final DocumentoRepositoryPort documentoRepository;
    private final StoragePort storagePort;
    private final DteXmlAssemblyService xmlAssembly;

    public DteXmlRegenerationService(
            DteCrudService dteCrudService,
            DocumentoRepositoryPort documentoRepository,
            StoragePort storagePort,
            DteXmlAssemblyService xmlAssembly
    ) {
        this.dteCrudService = dteCrudService;
        this.documentoRepository = documentoRepository;
        this.storagePort = storagePort;
        this.xmlAssembly = xmlAssembly;
    }

    /**
     * Regenerates the signed XML without assigning another folio or changing the DTE snapshot.
     */
    public DocumentMetadata regenerate(String documentId) throws Exception {
        UUID dteId = parseDocumentId(documentId);
        Dte dte = dteCrudService.findForStorage(dteId, null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DTE not found: " + documentId
                ));

        String objectKey = dteObjectKey(dte);
        DocumentMetadata initial = new DocumentMetadata(
                documentId,
                DocumentStatus.RECEIVED
        );
        initial.setFolio(dte.getFolio() == null ? null : dte.getFolio().toString());
        initial.setObjectKey(objectKey);
        documentoRepository.createIfAbsent(initial);

        OffsetDateTime staleBefore = OffsetDateTime.now()
                .minus(REGENERATION_CLAIM_LEASE);
        if (!documentoRepository.tryClaimRegeneration(documentId, staleBefore)) {
            throw new DocumentRegenerationConflictException(
                    "Signed XML regeneration is already running or the DTE cannot be regenerated"
            );
        }

        DocumentMetadata metadata = documentoRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document metadata disappeared during regeneration"
                ));
        metadata.setFolio(dte.getFolio() == null ? null : dte.getFolio().toString());
        metadata.setObjectKey(objectKey);

        byte[] xml;
        try {
            xml = xmlAssembly.build(dte).xml();
            metadata.setSha256(sha256(xml));
            metadata.setSizeBytes((long) xml.length);
            metadata.setLastError(null);
            documentoRepository.save(metadata);
        } catch (Exception exception) {
            markFailure(
                    metadata,
                    DocumentStatus.FAILED_FATAL,
                    "Signed XML regeneration failed"
            );
            throw exception;
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            String storedKey = storagePort.store(
                    objectKey,
                    input,
                    xml.length,
                    "application/xml"
            );
            metadata.setObjectKey(storedKey);
            metadata.setStatus(DocumentStatus.STORED);
            metadata.setLastError(null);
            return documentoRepository.save(metadata);
        } catch (Exception exception) {
            markFailure(
                    metadata,
                    DocumentStatus.FAILED_RECOVERABLE,
                    "Signed XML storage failed during regeneration"
            );
            throw exception;
        }
    }

    private UUID parseDocumentId(String documentId) {
        try {
            return UUID.fromString(documentId);
        } catch (Exception exception) {
            throw new IllegalArgumentException("DTE id must be a UUID", exception);
        }
    }

    private String dteObjectKey(Dte dte) {
        LocalDate emissionDate = dte.getFchEmis();
        String year = emissionDate == null
                ? "unknown"
                : String.valueOf(emissionDate.getYear());
        String month = emissionDate == null
                ? "unknown"
                : "%02d".formatted(emissionDate.getMonthValue());
        String folio = dte.getFolio() == null
                ? "unassigned"
                : dte.getFolio().toString();
        return "dte/%s/%s/%s-%s.xml".formatted(
                year,
                month,
                dte.getId(),
                folio
        );
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void markFailure(
            DocumentMetadata metadata,
            DocumentStatus status,
            String message
    ) {
        metadata.setStatus(status);
        metadata.setLastError(message);
        documentoRepository.save(metadata);
    }
}
