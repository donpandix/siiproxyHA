package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dte_reference")
public class DteReference {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dte_id", nullable = false)
    private Dte dte;

    @Column(name = "nro_lin_ref", nullable = false)
    private Integer nroLinRef;
    @Column(name = "tpo_doc_ref", length = 3, nullable = false)
    private String tpoDocRef;
    @Column(name = "folio_ref", length = 18)
    private String folioRef;
    @Column(name = "fch_ref")
    private LocalDate fchRef;
    @Column(name = "cod_ref", length = 2)
    private String codRef;
    @Column(name = "razon_ref", length = 90)
    private String razonRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DteReference() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Dte getDte() { return dte; }
    public void setDte(Dte dte) { this.dte = dte; }
    public Integer getNroLinRef() { return nroLinRef; }
    public void setNroLinRef(Integer nroLinRef) { this.nroLinRef = nroLinRef; }
    public String getTpoDocRef() { return tpoDocRef; }
    public void setTpoDocRef(String tpoDocRef) { this.tpoDocRef = tpoDocRef; }
    public String getFolioRef() { return folioRef; }
    public void setFolioRef(String folioRef) { this.folioRef = folioRef; }
    public LocalDate getFchRef() { return fchRef; }
    public void setFchRef(LocalDate fchRef) { this.fchRef = fchRef; }
    public String getCodRef() { return codRef; }
    public void setCodRef(String codRef) { this.codRef = codRef; }
    public String getRazonRef() { return razonRef; }
    public void setRazonRef(String razonRef) { this.razonRef = razonRef; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
