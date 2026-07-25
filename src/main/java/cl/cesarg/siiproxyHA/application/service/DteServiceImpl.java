package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class DteServiceImpl implements DteService {

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
    @Transactional
    public DocumentMetadata ingest(DteRequest request) throws Exception {
        // Idempotency: if documentId present and exists, return existing
        if (request.getDocumentId() != null) {
            var existing = documentoRepository.findByDocumentId(request.getDocumentId());
            if (existing.isPresent()) return existing.get();
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

            cafService.assignFolioToDte(
                    tenant.getId(),
                    dte.getId(),
                    request.getPuntoVenta(),
                    assignmentRequestId,
                    request.getAssignedTo()
            );

            dte = dteRepository.findById(dte.getId())
                    .orElseThrow(() -> new IllegalStateException("DTE not found after folio assignment"));

            if (documentId == null || documentId.isBlank()) {
                documentId = dte.getId().toString();
            }
        }

        DocumentMetadata meta = new DocumentMetadata(documentId, DocumentStatus.RECEIVED);
        if (dte != null && dte.getFolio() != null) {
            meta.setFolio(String.valueOf(dte.getFolio()));
        }

        // If XML provided, store it in storage. Otherwise, generate XML from DTE with assigned folio.
        if (request.getXmlBase64() != null && !request.getXmlBase64().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(request.getXmlBase64());
            String key = String.format("dte/%s.xml", documentId == null || documentId.isBlank() ? UUID.randomUUID() : documentId);
            try (var in = new ByteArrayInputStream(bytes)) {
                String objectKey = storagePort.store(key, in, bytes.length, "application/xml");
                meta.setObjectKey(objectKey);
                meta.setStatus(DocumentStatus.STORED);
            }
        } else if (dte != null) {
            byte[] bytes = generateXmlFromDte(dte);
            String key = String.format("dte/%s.xml", documentId == null || documentId.isBlank() ? dte.getId() : documentId);
            try (var in = new ByteArrayInputStream(bytes)) {
                String objectKey = storagePort.store(key, in, bytes.length, "application/xml");
                meta.setObjectKey(objectKey);
                meta.setStatus(DocumentStatus.STORED);
            }
        }

        if (meta.getDocumentId() == null || meta.getDocumentId().isBlank()) {
            meta.setDocumentId(UUID.randomUUID().toString());
        }

        meta = documentoRepository.save(meta);

        return meta;
    }

    @Override
    public DocumentMetadata store(Dte dte) throws Exception {
        String documentId = dte.getId().toString();
        Optional<DocumentMetadata> existing = documentoRepository.findByDocumentId(documentId);
        if (existing.isPresent()) return existing.get();

        byte[] bytes = generateXmlFromDte(dte);
        String key = String.format("dte/%s.xml", documentId);

        DocumentMetadata metadata = new DocumentMetadata(documentId, DocumentStatus.RECEIVED);
        metadata.setFolio(dte.getFolio() == null ? null : dte.getFolio().toString());
        try (var input = new ByteArrayInputStream(bytes)) {
            metadata.setObjectKey(storagePort.store(key, input, bytes.length, "application/xml"));
            metadata.setStatus(DocumentStatus.STORED);
        }
        return documentoRepository.save(metadata);
    }

    @Override
    public DocumentMetadata getStatus(String documentId) throws Exception {
        Optional<DocumentMetadata> meta = documentoRepository.findByDocumentId(documentId);
        return meta.orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public cl.cesarg.siiproxyHA.application.dto.DteXmlResponse getXml(String documentId, boolean presigned, int expiryMinutes) throws Exception {
        Optional<DocumentMetadata> metaOpt = documentoRepository.findByDocumentId(documentId);
        if (metaOpt.isEmpty()) {
            Optional<Dte> dteOpt = findDteByIdIfUuid(documentId);
            if (dteOpt.isPresent()) {
                byte[] xml = generateXmlFromDte(dteOpt.get());
                String xmlBase64 = Base64.getEncoder().encodeToString(xml);
                return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, xmlBase64, null);
            }
            throw new IllegalArgumentException("Document not found");
        }

        DocumentMetadata meta = metaOpt.get();

        String objectKey = meta.getObjectKey();
        if (objectKey == null) {
            Optional<Dte> dteOpt = findDteByIdIfUuid(documentId);
            if (dteOpt.isPresent()) {
                byte[] xml = generateXmlFromDte(dteOpt.get());
                String xmlBase64 = Base64.getEncoder().encodeToString(xml);
                return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, xmlBase64, null);
            }
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

    private Optional<Dte> findDteByIdIfUuid(String documentId) {
        try {
            return dteRepository.findById(UUID.fromString(documentId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private byte[] generateXmlFromDte(Dte dte) {
        return xmlAssembly.build(dte).xml();
    }
}
