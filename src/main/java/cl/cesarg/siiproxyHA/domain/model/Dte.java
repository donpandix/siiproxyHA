package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "dte")
public class Dte {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Tenant tenant;

    @Column(name = "tipo_dte", nullable = false)
    private Integer tipoDte;

    @Column(name = "folio", nullable = false)
    private Long folio;

    @Column(name = "fch_emis", nullable = false)
    private LocalDate fchEmis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Receptor receptor;

    @Column(name = "rut_recep", length = 12)
    private String rutRecep;
    @Column(name = "rzn_soc_recep", length = 100)
    private String rznSocRecep;
    @Column(name = "dir_recep", length = 70)
    private String dirRecep;
    @Column(name = "cmna_recep", length = 20)
    private String cmnaRecep;
    @Column(name = "ciudad_recep", length = 20)
    private String ciudadRecep;
    @Column(name = "correo_recep", length = 80)
    private String correoRecep;

    @Column(name = "mnt_neto")
    private Long mntNeto;
    @Column(name = "mnt_exe")
    private Long mntExe;
    @Column(name = "tasa_iva", precision = 5, scale = 2)
    private BigDecimal tasaIva;
    @Column(name = "iva")
    private Long iva;
    @Column(name = "mnt_total", nullable = false)
    private Long mntTotal;

    @Column(name = "sii_track_id")
    private Long siiTrackId;
    @Column(name = "sii_estado", length = 10)
    private String siiEstado;
    @Column(name = "sii_glosa", length = 255)
    private String siiGlosa;

    @Column(name = "enviado_at")
    private Instant enviadoAt;
    @Column(name = "last_status_at")
    private Instant lastStatusAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folio_assignment_id")
    private FolioAssignment folioAssignment;

    @OneToMany(mappedBy = "dte", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DteItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "dte", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DteReference> references = new ArrayList<>();

    public Dte() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Integer getTipoDte() { return tipoDte; }
    public void setTipoDte(Integer tipoDte) { this.tipoDte = tipoDte; }
    public Long getFolio() { return folio; }
    public void setFolio(Long folio) { this.folio = folio; }
    public LocalDate getFchEmis() { return fchEmis; }
    public void setFchEmis(LocalDate fchEmis) { this.fchEmis = fchEmis; }
    public Receptor getReceptor() { return receptor; }
    public void setReceptor(Receptor receptor) { this.receptor = receptor; }
    public String getRutRecep() { return rutRecep; }
    public void setRutRecep(String rutRecep) { this.rutRecep = rutRecep; }
    public String getRznSocRecep() { return rznSocRecep; }
    public void setRznSocRecep(String rznSocRecep) { this.rznSocRecep = rznSocRecep; }
    public String getDirRecep() { return dirRecep; }
    public void setDirRecep(String dirRecep) { this.dirRecep = dirRecep; }
    public String getCmnaRecep() { return cmnaRecep; }
    public void setCmnaRecep(String cmnaRecep) { this.cmnaRecep = cmnaRecep; }
    public String getCiudadRecep() { return ciudadRecep; }
    public void setCiudadRecep(String ciudadRecep) { this.ciudadRecep = ciudadRecep; }
    public String getCorreoRecep() { return correoRecep; }
    public void setCorreoRecep(String correoRecep) { this.correoRecep = correoRecep; }
    public Long getMntNeto() { return mntNeto; }
    public void setMntNeto(Long mntNeto) { this.mntNeto = mntNeto; }
    public Long getMntExe() { return mntExe; }
    public void setMntExe(Long mntExe) { this.mntExe = mntExe; }
    public BigDecimal getTasaIva() { return tasaIva; }
    public void setTasaIva(BigDecimal tasaIva) { this.tasaIva = tasaIva; }
    public Long getIva() { return iva; }
    public void setIva(Long iva) { this.iva = iva; }
    public Long getMntTotal() { return mntTotal; }
    public void setMntTotal(Long mntTotal) { this.mntTotal = mntTotal; }
    public Long getSiiTrackId() { return siiTrackId; }
    public void setSiiTrackId(Long siiTrackId) { this.siiTrackId = siiTrackId; }
    public String getSiiEstado() { return siiEstado; }
    public void setSiiEstado(String siiEstado) { this.siiEstado = siiEstado; }
    public String getSiiGlosa() { return siiGlosa; }
    public void setSiiGlosa(String siiGlosa) { this.siiGlosa = siiGlosa; }
    public Instant getEnviadoAt() { return enviadoAt; }
    public void setEnviadoAt(Instant enviadoAt) { this.enviadoAt = enviadoAt; }
    public Instant getLastStatusAt() { return lastStatusAt; }
    public void setLastStatusAt(Instant lastStatusAt) { this.lastStatusAt = lastStatusAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<DteItem> getItems() { return items; }
    public void setItems(List<DteItem> items) { this.items = items; }
    public List<DteReference> getReferences() { return references; }
    public void setReferences(List<DteReference> references) { this.references = references; }
    public FolioAssignment getFolioAssignment() { return folioAssignment; }
    public void setFolioAssignment(FolioAssignment folioAssignment) { this.folioAssignment = folioAssignment; }
}
