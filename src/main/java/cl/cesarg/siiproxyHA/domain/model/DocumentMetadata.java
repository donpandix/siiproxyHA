package cl.cesarg.siiproxyHA.domain.model;

import java.time.OffsetDateTime;

public class DocumentMetadata {

    private Long id;
    private String documentId;
    private String folio;
    private DocumentStatus status;
    private String objectKey;
    private OffsetDateTime createdAt;

    public DocumentMetadata() {}

    public DocumentMetadata(String documentId, DocumentStatus status) {
        this.documentId = documentId;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
