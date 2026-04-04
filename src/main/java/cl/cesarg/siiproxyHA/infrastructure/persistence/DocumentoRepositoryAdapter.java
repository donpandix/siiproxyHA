package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class DocumentoRepositoryAdapter implements DocumentoRepositoryPort {

    private final DocumentoJpaRepository jpa;

    public DocumentoRepositoryAdapter(DocumentoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DocumentMetadata save(DocumentMetadata meta) {
        DocumentoEntity e = new DocumentoEntity();
        e.setDocumentId(meta.getDocumentId());
        e.setFolio(meta.getFolio());
        e.setStatus(meta.getStatus() == null ? null : meta.getStatus().name());
        e.setObjectKey(meta.getObjectKey());
        e.setCreatedAt(meta.getCreatedAt() == null ? OffsetDateTime.now() : meta.getCreatedAt());
        DocumentoEntity saved = jpa.save(e);
        meta.setId(saved.getId());
        return meta;
    }

    @Override
    public Optional<DocumentMetadata> findByDocumentId(String documentId) {
        return jpa.findByDocumentId(documentId).map(e -> {
            DocumentMetadata m = new DocumentMetadata();
            m.setId(e.getId());
            m.setDocumentId(e.getDocumentId());
            if (e.getStatus() != null) m.setStatus(DocumentStatus.valueOf(e.getStatus()));
            m.setObjectKey(e.getObjectKey());
            m.setFolio(e.getFolio());
            m.setCreatedAt(e.getCreatedAt());
            return m;
        });
    }
}
