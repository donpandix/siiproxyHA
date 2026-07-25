package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentProcessingHistoryRepository
        extends JpaRepository<DocumentProcessingHistoryEntity, Long> {

    List<DocumentProcessingHistoryEntity> findByDocumentIdOrderByCreatedAtAsc(String documentId);
}
