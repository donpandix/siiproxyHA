package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.SiiSubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sii_submission")
public class SiiSubmissionEntity {

    @Id
    private UUID id;

    @Column(name = "dte_id", nullable = false)
    private UUID dteId;

    @Column(name = "document_id", nullable = false, length = 200)
    private String documentId;

    @Column(name = "signing_credential_id", nullable = false)
    private UUID signingCredentialId;

    @Column(name = "environment", nullable = false, length = 20)
    private String environment;

    @Column(name = "artifact_key", nullable = false, length = 500)
    private String artifactKey;

    @Column(name = "artifact_sha256", nullable = false, length = 64)
    private String artifactSha256;

    @Column(name = "artifact_size_bytes", nullable = false)
    private Long artifactSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SiiSubmissionStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "status_query_count", nullable = false)
    private Integer statusQueryCount;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "sii_status", length = 10)
    private String siiStatus;

    @Column(name = "sii_glosa", length = 500)
    private String siiGlosa;

    @Column(name = "numero_atencion", length = 40)
    private String numeroAtencion;

    @Column(name = "informed_count")
    private Integer informedCount;

    @Column(name = "accepted_count")
    private Integer acceptedCount;

    @Column(name = "rejected_count")
    private Integer rejectedCount;

    @Column(name = "repair_count")
    private Integer repairCount;

    @Column(name = "remote_http_status")
    private Integer remoteHttpStatus;

    @Column(name = "response_object_key", length = 500)
    private String responseObjectKey;

    @Column(name = "response_sha256", length = 64)
    private String responseSha256;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDteId() { return dteId; }
    public void setDteId(UUID dteId) { this.dteId = dteId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public UUID getSigningCredentialId() { return signingCredentialId; }
    public void setSigningCredentialId(UUID signingCredentialId) { this.signingCredentialId = signingCredentialId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getArtifactKey() { return artifactKey; }
    public void setArtifactKey(String artifactKey) { this.artifactKey = artifactKey; }
    public String getArtifactSha256() { return artifactSha256; }
    public void setArtifactSha256(String artifactSha256) { this.artifactSha256 = artifactSha256; }
    public Long getArtifactSizeBytes() { return artifactSizeBytes; }
    public void setArtifactSizeBytes(Long artifactSizeBytes) { this.artifactSizeBytes = artifactSizeBytes; }
    public SiiSubmissionStatus getStatus() { return status; }
    public void setStatus(SiiSubmissionStatus status) { this.status = status; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getStatusQueryCount() { return statusQueryCount; }
    public void setStatusQueryCount(Integer statusQueryCount) { this.statusQueryCount = statusQueryCount; }
    public Long getTrackId() { return trackId; }
    public void setTrackId(Long trackId) { this.trackId = trackId; }
    public String getSiiStatus() { return siiStatus; }
    public void setSiiStatus(String siiStatus) { this.siiStatus = siiStatus; }
    public String getSiiGlosa() { return siiGlosa; }
    public void setSiiGlosa(String siiGlosa) { this.siiGlosa = siiGlosa; }
    public String getNumeroAtencion() { return numeroAtencion; }
    public void setNumeroAtencion(String numeroAtencion) { this.numeroAtencion = numeroAtencion; }
    public Integer getInformedCount() { return informedCount; }
    public void setInformedCount(Integer informedCount) { this.informedCount = informedCount; }
    public Integer getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(Integer acceptedCount) { this.acceptedCount = acceptedCount; }
    public Integer getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(Integer rejectedCount) { this.rejectedCount = rejectedCount; }
    public Integer getRepairCount() { return repairCount; }
    public void setRepairCount(Integer repairCount) { this.repairCount = repairCount; }
    public Integer getRemoteHttpStatus() { return remoteHttpStatus; }
    public void setRemoteHttpStatus(Integer remoteHttpStatus) { this.remoteHttpStatus = remoteHttpStatus; }
    public String getResponseObjectKey() { return responseObjectKey; }
    public void setResponseObjectKey(String responseObjectKey) { this.responseObjectKey = responseObjectKey; }
    public String getResponseSha256() { return responseSha256; }
    public void setResponseSha256(String responseSha256) { this.responseSha256 = responseSha256; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
