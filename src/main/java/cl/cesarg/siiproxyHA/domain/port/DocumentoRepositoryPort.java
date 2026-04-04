package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import java.util.Optional;

public interface DocumentoRepositoryPort {
    DocumentMetadata save(DocumentMetadata meta);
    Optional<DocumentMetadata> findByDocumentId(String documentId);
}
