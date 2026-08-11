package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import java.time.OffsetDateTime;

public class PublicDocumentResponse {

    private String documentId;
    private String type;
    private String status;
    private Long folio;
    private PublicDocumentSiiStatus sii;
    private OffsetDateTime createdAt;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFolio() { return folio; }
    public void setFolio(Long folio) { this.folio = folio; }
    public PublicDocumentSiiStatus getSii() { return sii; }
    public void setSii(PublicDocumentSiiStatus sii) { this.sii = sii; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
