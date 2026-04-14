package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folio_pool")
public class FolioPool {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tipo_dte", nullable = false)
    private Integer tipoDte;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caf_id", nullable = false)
    private Caf caf;

    @Column(name = "folio_desde", nullable = false)
    private Long folioDesde;

    @Column(name = "folio_hasta", nullable = false)
    private Long folioHasta;

    @Column(name = "next_folio", nullable = false)
    private Long nextFolio;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FolioPool() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Integer getTipoDte() { return tipoDte; }
    public void setTipoDte(Integer tipoDte) { this.tipoDte = tipoDte; }
    public Integer getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(Integer puntoVenta) { this.puntoVenta = puntoVenta; }
    public Caf getCaf() { return caf; }
    public void setCaf(Caf caf) { this.caf = caf; }
    public Long getFolioDesde() { return folioDesde; }
    public void setFolioDesde(Long folioDesde) { this.folioDesde = folioDesde; }
    public Long getFolioHasta() { return folioHasta; }
    public void setFolioHasta(Long folioHasta) { this.folioHasta = folioHasta; }
    public Long getNextFolio() { return nextFolio; }
    public void setNextFolio(Long nextFolio) { this.nextFolio = nextFolio; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
