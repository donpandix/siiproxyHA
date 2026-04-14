package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "caf")
public class Caf {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tipo_dte", nullable = false)
    private Integer tipoDte;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta = 1;

    @Column(name = "folio_desde", nullable = false)
    private Long folioDesde;

    @Column(name = "folio_hasta", nullable = false)
    private Long folioHasta;

    @Column(name = "caf_path", length = 500, nullable = false)
    private String cafPath;

    @Column(name = "caf_sha256", length = 64)
    private String cafSha256;

    @Column(name = "rut_emisor", length = 12)
    private String rutEmisor;

    @Column(name = "fch_autorizacion")
    private LocalDate fchAutorizacion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Caf() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Integer getTipoDte() { return tipoDte; }
    public void setTipoDte(Integer tipoDte) { this.tipoDte = tipoDte; }
    public Integer getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(Integer puntoVenta) { this.puntoVenta = puntoVenta; }
    public Long getFolioDesde() { return folioDesde; }
    public void setFolioDesde(Long folioDesde) { this.folioDesde = folioDesde; }
    public Long getFolioHasta() { return folioHasta; }
    public void setFolioHasta(Long folioHasta) { this.folioHasta = folioHasta; }
    public String getCafPath() { return cafPath; }
    public void setCafPath(String cafPath) { this.cafPath = cafPath; }
    public String getCafSha256() { return cafSha256; }
    public void setCafSha256(String cafSha256) { this.cafSha256 = cafSha256; }
    public String getRutEmisor() { return rutEmisor; }
    public void setRutEmisor(String rutEmisor) { this.rutEmisor = rutEmisor; }
    public LocalDate getFchAutorizacion() { return fchAutorizacion; }
    public void setFchAutorizacion(LocalDate fchAutorizacion) { this.fchAutorizacion = fchAutorizacion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
