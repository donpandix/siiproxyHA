package cl.cesarg.siiproxyHA.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class DocumentMetadata {

    private Long id;
    private String documentId;
    private String folio;
    private DocumentStatus status;
    private String objectKey;
    private String sha256;
    private Long sizeBytes;
    private Integer attemptCount;
    private String lastError;
    private UUID signingCredentialId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public DocumentMetadata() {}

    public DocumentMetadata(String documentId, DocumentStatus status) {
        this.documentId = documentId;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
        this.attemptCount = 0;
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
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public UUID getSigningCredentialId() { return signingCredentialId; }
    public void setSigningCredentialId(UUID signingCredentialId) { this.signingCredentialId = signingCredentialId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
