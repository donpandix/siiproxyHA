package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dte_item")
public class DteItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dte_id", nullable = false)
    private Dte dte;

    @Column(name = "nro_lin_det", nullable = false)
    private Integer nroLinDet;
    @Column(name = "nmb_item", length = 80, nullable = false)
    private String nmbItem;
    @Column(name = "dsc_item", length = 1000)
    private String dscItem;
    @Column(name = "qty_item")
    private Double qtyItem;
    @Column(name = "unmd_item", length = 4)
    private String unmdItem;
    @Column(name = "prc_item")
    private Double prcItem;
    @Column(name = "monto_item")
    private Long montoItem;
    @Column(name = "ind_exe")
    private Integer indExe;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DteItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Dte getDte() { return dte; }
    public void setDte(Dte dte) { this.dte = dte; }
    public Integer getNroLinDet() { return nroLinDet; }
    public void setNroLinDet(Integer nroLinDet) { this.nroLinDet = nroLinDet; }
    public String getNmbItem() { return nmbItem; }
    public void setNmbItem(String nmbItem) { this.nmbItem = nmbItem; }
    public String getDscItem() { return dscItem; }
    public void setDscItem(String dscItem) { this.dscItem = dscItem; }
    public Double getQtyItem() { return qtyItem; }
    public void setQtyItem(Double qtyItem) { this.qtyItem = qtyItem; }
    public String getUnmdItem() { return unmdItem; }
    public void setUnmdItem(String unmdItem) { this.unmdItem = unmdItem; }
    public Double getPrcItem() { return prcItem; }
    public void setPrcItem(Double prcItem) { this.prcItem = prcItem; }
    public Long getMontoItem() { return montoItem; }
    public void setMontoItem(Long montoItem) { this.montoItem = montoItem; }
    public Integer getIndExe() { return indExe; }
    public void setIndExe(Integer indExe) { this.indExe = indExe; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
