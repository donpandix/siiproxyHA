package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class DteServiceImpl implements DteService {

    private static final Duration STORE_CLAIM_LEASE = Duration.ofMinutes(5);

    private final DocumentoRepositoryPort documentoRepository;
    private final StoragePort storagePort;
    private final DteRepository dteRepository;
    private final TenantRepository tenantRepository;
    private final CafService cafService;
    private final DteXmlAssemblyService xmlAssembly;

    public DteServiceImpl(DocumentoRepositoryPort documentoRepository,
                          StoragePort storagePort,
                          DteRepository dteRepository,
                          TenantRepository tenantRepository,
                          CafService cafService,
                          DteXmlAssemblyService xmlAssembly) {
        this.documentoRepository = documentoRepository;
        this.storagePort = storagePort;
        this.dteRepository = dteRepository;
        this.tenantRepository = tenantRepository;
        this.cafService = cafService;
        this.xmlAssembly = xmlAssembly;
    }

    @Override
    public DocumentMetadata ingest(DteRequest request) throws Exception {
        if (request.getDocumentId() != null) {
            var existing = documentoRepository.findByDocumentId(request.getDocumentId());
            if (existing.filter(metadata -> metadata.getStatus() == DocumentStatus.STORED).isPresent()) {
                return existing.get();
            }
        }

        // Basic business validation (more rules in domain)
        if (request.getEmitterRUT() == null || request.getReceiverRUT() == null) {
            throw new IllegalArgumentException("emitterRUT and receiverRUT are required");
        }

        String documentId = request.getDocumentId();
        Dte dte = null;

        if (Boolean.TRUE.equals(request.getAssignFolio())) {
            Tenant tenant = tenantRepository.findByRutEmisor(request.getEmitterRUT())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for emitterRUT"));

            dte = new Dte();
            dte.setId(UUID.randomUUID());
            dte.setTenant(tenant);
            dte.setTipoDte(request.getTipoDte() == null ? 33 : request.getTipoDte());
            dte.setFolio(0L);
            dte.setFchEmis(LocalDate.now());
            dte.setRutRecep(request.getReceiverRUT());
            dte.setRznSocRecep(request.getReceiverRUT());
            dte.setMntTotal(0L);
            dte.setCreatedAt(Instant.now());
            dte.setUpdatedAt(Instant.now());
            dte = dteRepository.save(dte);

            String assignmentRequestId = request.getRequestId();
            if (assignmentRequestId == null || assignmentRequestId.isBlank()) {
                assignmentRequestId = request.getDocumentId();
            }

            FolioAssignment assignment = cafService.assignFolioToDte(
                    tenant.getId(),
                    dte.getId(),
                    request.getPuntoVenta(),
                    assignmentRequestId,
                    request.getAssignedTo()
            );
            dte.setFolio(assignment.getFolio());
            dte.setFolioAssignment(assignment);

            if (documentId == null || documentId.isBlank()) {
                documentId = dte.getId().toString();
            }
        }

        if (documentId == null || documentId.isBlank()) {
            documentId = UUID.randomUUID().toString();
        }

        if (request.getXmlBase64() != null && !request.getXmlBase64().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(request.getXmlBase64());
            String folio = dte == null || dte.getFolio() == null ? null : dte.getFolio().toString();
            return storeArtifact(documentId, folio, "dte/" + documentId + ".xml", () -> bytes);
        }

        if (dte != null) {
            return store(dte);
        }

        return documentoRepository.createIfAbsent(new DocumentMetadata(documentId, DocumentStatus.RECEIVED));
    }

    @Override
    public DocumentMetadata store(Dte dte) throws Exception {
        String documentId = dte.getId().toString();
        String folio = dte.getFolio() == null ? null : dte.getFolio().toString();
        String key = dteObjectKey(dte);
        return storeArtifact(documentId, folio, key, () -> generateXmlFromDte(dte));
    }

    @Override
    public DocumentMetadata getStatus(String documentId) throws Exception {
        Optional<DocumentMetadata> meta = documentoRepository.findByDocumentId(documentId);
        return meta.orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Override
    public cl.cesarg.siiproxyHA.application.dto.DteXmlResponse getXml(String documentId, boolean presigned, int expiryMinutes) throws Exception {
        Optional<DocumentMetadata> metaOpt = documentoRepository.findByDocumentId(documentId);
        if (metaOpt.isEmpty()) {
            throw new IllegalArgumentException("Document not found");
        }

        DocumentMetadata meta = metaOpt.get();

        String objectKey = meta.getObjectKey();
        if (meta.getStatus() != DocumentStatus.STORED || objectKey == null) {
            throw new IllegalArgumentException("No object stored for document");
        }

        if (presigned) {
            String url = storagePort.presignedUrl(objectKey, expiryMinutes);
            return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, null, url);
        } else {
            byte[] data = storagePort.get(objectKey);
            String xmlBase64 = Base64.getEncoder().encodeToString(data);
            return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, xmlBase64, null);
        }
    }

    private DocumentMetadata storeArtifact(String documentId,
                                           String folio,
                                           String objectKey,
                                           ArtifactProducer producer) throws Exception {
        DocumentMetadata initial = new DocumentMetadata(documentId, DocumentStatus.RECEIVED);
        initial.setFolio(folio);
        initial.setObjectKey(objectKey);
        DocumentMetadata metadata = documentoRepository.createIfAbsent(initial);
        if (metadata.getStatus() == DocumentStatus.STORED) {
            return metadata;
        }

        boolean retry = metadata.getAttemptCount() != null && metadata.getAttemptCount() > 0;
        OffsetDateTime staleBefore = OffsetDateTime.now().minus(STORE_CLAIM_LEASE);
        if (!documentoRepository.tryClaimStore(documentId, staleBefore)) {
            return documentoRepository.findByDocumentId(documentId).orElse(metadata);
        }

        metadata = documentoRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalStateException("Document metadata disappeared during storage"));

        if (retry && metadata.getSha256() != null) {
            DocumentMetadata recovered = recoverStoredArtifact(metadata);
            if (recovered != null) {
                return recovered;
            }
        }

        byte[] bytes;
        try {
            bytes = producer.produce();
        } catch (Exception exception) {
            markFailure(metadata, DocumentStatus.FAILED_FATAL, "XML generation or validation failed");
            throw exception;
        }

        metadata.setSha256(sha256(bytes));
        metadata.setSizeBytes((long) bytes.length);
        metadata.setStatus(DocumentStatus.PENDING_STORE);
        metadata.setLastError(null);
        documentoRepository.save(metadata);

        try (var input = new ByteArrayInputStream(bytes)) {
            String storedKey = storagePort.store(objectKey, input, bytes.length, "application/xml");
            metadata.setObjectKey(storedKey);
            metadata.setStatus(DocumentStatus.STORED);
            metadata.setLastError(null);
            return documentoRepository.save(metadata);
        } catch (Exception exception) {
            markFailure(metadata, DocumentStatus.FAILED_RECOVERABLE, "Object storage write failed");
            throw exception;
        }
    }

    private DocumentMetadata recoverStoredArtifact(DocumentMetadata metadata) throws Exception {
        try {
            byte[] stored = storagePort.get(metadata.getObjectKey());
            if (stored == null || stored.length == 0) {
                return null;
            }
            String storedHash = sha256(stored);
            if (metadata.getSha256() != null && !metadata.getSha256().equals(storedHash)) {
                markFailure(metadata, DocumentStatus.FAILED_FATAL, "Stored artifact checksum mismatch");
                throw new IllegalStateException("Stored artifact checksum does not match document metadata");
            }
            metadata.setSha256(storedHash);
            metadata.setSizeBytes((long) stored.length);
            metadata.setStatus(DocumentStatus.STORED);
            metadata.setLastError(null);
            return documentoRepository.save(metadata);
        } catch (ObjectStorageException exception) {
            return null;
        }
    }

    private void markFailure(DocumentMetadata metadata, DocumentStatus status, String message) {
        metadata.setStatus(status);
        metadata.setLastError(message);
        documentoRepository.save(metadata);
    }

    private String dteObjectKey(Dte dte) {
        LocalDate emissionDate = dte.getFchEmis();
        String year = emissionDate == null ? "unknown" : String.valueOf(emissionDate.getYear());
        String month = emissionDate == null ? "unknown" : "%02d".formatted(emissionDate.getMonthValue());
        String folio = dte.getFolio() == null ? "unassigned" : dte.getFolio().toString();
        return "dte/%s/%s/%s-%s.xml".formatted(year, month, dte.getId(), folio);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] generateXmlFromDte(Dte dte) {
        return xmlAssembly.build(dte).xml();
    }

    @FunctionalInterface
    private interface ArtifactProducer {
        byte[] produce() throws Exception;
    }
}
