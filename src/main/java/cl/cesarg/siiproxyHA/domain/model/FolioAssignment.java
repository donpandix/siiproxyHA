package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folio_assignment")
public class FolioAssignment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tipo_dte", nullable = false)
    private Integer tipoDte;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta = 1;

    @Column(name = "folio", nullable = false)
    private Long folio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folio_pool_id", nullable = false)
    private FolioPool folioPool;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "assigned_to", length = 50)
    private String assignedTo = "SYSTEM";

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dte_id")
    private Dte dte;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ASSIGNED";

    @Column(name = "note", length = 255)
    private String note;

    public FolioAssignment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Integer getTipoDte() { return tipoDte; }
    public void setTipoDte(Integer tipoDte) { this.tipoDte = tipoDte; }
    public Integer getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(Integer puntoVenta) { this.puntoVenta = puntoVenta; }
    public Long getFolio() { return folio; }
    public void setFolio(Long folio) { this.folio = folio; }
    public FolioPool getFolioPool() { return folioPool; }
    public void setFolioPool(FolioPool folioPool) { this.folioPool = folioPool; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public Dte getDte() { return dte; }
    public void setDte(Dte dte) { this.dte = dte; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
