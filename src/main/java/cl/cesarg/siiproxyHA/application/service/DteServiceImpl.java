package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Optional;

@Service
public class DteServiceImpl implements DteService {

    private final DocumentoRepositoryPort documentoRepository;
    private final StoragePort storagePort;

    public DteServiceImpl(DocumentoRepositoryPort documentoRepository, StoragePort storagePort) {
        this.documentoRepository = documentoRepository;
        this.storagePort = storagePort;
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

        DocumentMetadata meta = new DocumentMetadata(request.getDocumentId(), DocumentStatus.RECEIVED);
        meta = documentoRepository.save(meta);

        // If XML provided, store it in storage and update metadata
        if (request.getXmlBase64() != null && !request.getXmlBase64().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(request.getXmlBase64());
            String key = String.format("dte/%s.xml", meta.getDocumentId() == null ? meta.getId() : meta.getDocumentId());
            try (var in = new ByteArrayInputStream(bytes)) {
                String objectKey = storagePort.store(key, in, bytes.length, "application/xml");
                meta.setObjectKey(objectKey);
                meta.setStatus(DocumentStatus.STORED);
                documentoRepository.save(meta);
            }
        }

        return meta;
    }

    @Override
    public DocumentMetadata getStatus(String documentId) throws Exception {
        Optional<DocumentMetadata> meta = documentoRepository.findByDocumentId(documentId);
        return meta.orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Override
    public cl.cesarg.siiproxyHA.application.dto.DteXmlResponse getXml(String documentId, boolean presigned, int expiryMinutes) throws Exception {
        Optional<DocumentMetadata> metaOpt = documentoRepository.findByDocumentId(documentId);
        DocumentMetadata meta = metaOpt.orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String objectKey = meta.getObjectKey();
        if (objectKey == null) {
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
}
