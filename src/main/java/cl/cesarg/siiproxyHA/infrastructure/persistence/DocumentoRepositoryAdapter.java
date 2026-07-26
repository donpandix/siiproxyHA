package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class DocumentoRepositoryAdapter implements DocumentoRepositoryPort {

    private final DocumentoJpaRepository jpa;
    private final DocumentProcessingHistoryRepository historyRepository;

    public DocumentoRepositoryAdapter(DocumentoJpaRepository jpa,
                                      DocumentProcessingHistoryRepository historyRepository) {
        this.jpa = jpa;
        this.historyRepository = historyRepository;
    }

    @Override
    public DocumentMetadata save(DocumentMetadata meta) {
        DocumentoEntity e = meta.getId() == null
                ? jpa.findByDocumentId(meta.getDocumentId()).orElseGet(DocumentoEntity::new)
                : jpa.findById(meta.getId()).orElseGet(DocumentoEntity::new);
        String previousStatus = e.getStatus();
        if (meta.getId() != null) {
            e.setId(meta.getId());
        }
        e.setDocumentId(meta.getDocumentId());
        e.setFolio(meta.getFolio());
        e.setStatus(meta.getStatus() == null ? null : meta.getStatus().name());
        e.setObjectKey(meta.getObjectKey());
        e.setSha256(meta.getSha256());
        e.setSizeBytes(meta.getSizeBytes());
        e.setAttemptCount(meta.getAttemptCount() == null ? 0 : meta.getAttemptCount());
        e.setLastError(meta.getLastError());
        e.setCreatedAt(meta.getCreatedAt() == null ? OffsetDateTime.now() : meta.getCreatedAt());
        e.setUpdatedAt(OffsetDateTime.now());
        DocumentoEntity saved = jpa.save(e);
        meta.setId(saved.getId());
        meta.setUpdatedAt(saved.getUpdatedAt());
        recordTransition(meta.getDocumentId(), previousStatus, saved.getStatus(), "State persisted");
        return meta;
    }

    @Override
    public DocumentMetadata createIfAbsent(DocumentMetadata meta) {
        Optional<DocumentMetadata> existing = findByDocumentId(meta.getDocumentId());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return saveAndFlush(meta);
        } catch (DataIntegrityViolationException exception) {
            return findByDocumentId(meta.getDocumentId()).orElseThrow(() -> exception);
        }
    }

    @Override
    public Optional<DocumentMetadata> findByDocumentId(String documentId) {
        return jpa.findByDocumentId(documentId).map(e -> {
            DocumentMetadata m = new DocumentMetadata();
            m.setId(e.getId());
            m.setDocumentId(e.getDocumentId());
            if (e.getStatus() != null) m.setStatus(DocumentStatus.valueOf(e.getStatus()));
            m.setObjectKey(e.getObjectKey());
            m.setSha256(e.getSha256());
            m.setSizeBytes(e.getSizeBytes());
            m.setAttemptCount(e.getAttemptCount());
            m.setLastError(e.getLastError());
            m.setFolio(e.getFolio());
            m.setCreatedAt(e.getCreatedAt());
            m.setUpdatedAt(e.getUpdatedAt());
            return m;
        });
    }

    @Override
    public boolean tryClaimStore(String documentId, OffsetDateTime staleBefore) {
        Optional<DocumentoEntity> before = jpa.findByDocumentId(documentId);
        boolean claimed = jpa.claimStore(documentId, OffsetDateTime.now(), staleBefore) == 1;
        if (claimed) {
            recordTransition(
                    documentId,
                    before.map(DocumentoEntity::getStatus).orElse(null),
                    DocumentStatus.PENDING_STORE.name(),
                    "Storage attempt claimed"
            );
        }
        return claimed;
    }

    @Override
    public boolean tryClaimRegeneration(String documentId, OffsetDateTime staleBefore) {
        Optional<DocumentoEntity> before = jpa.findByDocumentId(documentId);
        boolean claimed = jpa.claimRegeneration(
                documentId,
                OffsetDateTime.now(),
                staleBefore
        ) == 1;
        if (claimed) {
            recordTransition(
                    documentId,
                    before.map(DocumentoEntity::getStatus).orElse(null),
                    DocumentStatus.PENDING_STORE.name(),
                    "Signed XML regeneration claimed"
            );
        }
        return claimed;
    }

    private DocumentMetadata saveAndFlush(DocumentMetadata meta) {
        DocumentoEntity entity = new DocumentoEntity();
        entity.setDocumentId(meta.getDocumentId());
        entity.setFolio(meta.getFolio());
        entity.setStatus(meta.getStatus() == null ? null : meta.getStatus().name());
        entity.setObjectKey(meta.getObjectKey());
        entity.setSha256(meta.getSha256());
        entity.setSizeBytes(meta.getSizeBytes());
        entity.setAttemptCount(meta.getAttemptCount() == null ? 0 : meta.getAttemptCount());
        entity.setLastError(meta.getLastError());
        entity.setCreatedAt(meta.getCreatedAt() == null ? OffsetDateTime.now() : meta.getCreatedAt());
        entity.setUpdatedAt(meta.getUpdatedAt() == null ? OffsetDateTime.now() : meta.getUpdatedAt());
        DocumentoEntity saved = jpa.saveAndFlush(entity);
        meta.setId(saved.getId());
        meta.setUpdatedAt(saved.getUpdatedAt());
        recordTransition(meta.getDocumentId(), null, saved.getStatus(), "Document metadata created");
        return meta;
    }

    private void recordTransition(String documentId,
                                  String fromState,
                                  String toState,
                                  String notes) {
        if (toState == null || toState.equals(fromState)) {
            return;
        }
        DocumentProcessingHistoryEntity event = new DocumentProcessingHistoryEntity();
        event.setDocumentId(documentId);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setActor("DTE_STORAGE");
        event.setNotes(notes);
        event.setCreatedAt(OffsetDateTime.now());
        historyRepository.save(event);
    }
}
