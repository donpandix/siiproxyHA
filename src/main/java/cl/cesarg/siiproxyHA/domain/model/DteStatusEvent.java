package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dte_status_event")
public class DteStatusEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dte_id", nullable = false)
    private Dte dte;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "sii_code", length = 10)
    private String siiCode;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DteStatusEvent() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Dte getDte() { return dte; }
    public void setDte(Dte dte) { this.dte = dte; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSiiCode() { return siiCode; }
    public void setSiiCode(String siiCode) { this.siiCode = siiCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
