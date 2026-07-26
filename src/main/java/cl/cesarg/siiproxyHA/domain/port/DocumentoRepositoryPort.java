package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface DocumentoRepositoryPort {
    DocumentMetadata save(DocumentMetadata meta);
    DocumentMetadata createIfAbsent(DocumentMetadata meta);
    Optional<DocumentMetadata> findByDocumentId(String documentId);
    boolean tryClaimStore(String documentId, OffsetDateTime staleBefore);
    boolean tryClaimRegeneration(String documentId, OffsetDateTime staleBefore);
}
