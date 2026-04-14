package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dte_artifact")
public class DteArtifact {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dte_id", nullable = false)
    private Dte dte;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DteArtifact() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Dte getDte() { return dte; }
    public void setDte(Dte dte) { this.dte = dte; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
