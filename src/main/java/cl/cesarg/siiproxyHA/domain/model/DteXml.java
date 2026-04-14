package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dte_xml")
public class DteXml {

    @Id
    @Column(name = "dte_id")
    private UUID dteId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "dte_id")
    private Dte dte;

    @Column(name = "xml_documento", columnDefinition = "text", nullable = false)
    private String xmlDocumento;

    @Column(name = "xml_envio", columnDefinition = "text")
    private String xmlEnvio;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DteXml() {}

    public UUID getDteId() { return dteId; }
    public void setDteId(UUID dteId) { this.dteId = dteId; }
    public Dte getDte() { return dte; }
    public void setDte(Dte dte) { this.dte = dte; }
    public String getXmlDocumento() { return xmlDocumento; }
    public void setXmlDocumento(String xmlDocumento) { this.xmlDocumento = xmlDocumento; }
    public String getXmlEnvio() { return xmlEnvio; }
    public void setXmlEnvio(String xmlEnvio) { this.xmlEnvio = xmlEnvio; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
