package cl.cesarg.siiproxyHA.application.dto;

import jakarta.validation.constraints.NotBlank;

public class DteRequest {

    private String documentId;

    @NotBlank
    private String emitterRUT;

    @NotBlank
    private String receiverRUT;

    // Optional base64 XML payload
    private String xmlBase64;

    // Optional metadata for automatic folio assignment
    private Boolean assignFolio = true;
    private Integer tipoDte;
    private Integer puntoVenta;
    private String requestId;
    private String assignedTo;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getEmitterRUT() { return emitterRUT; }
    public void setEmitterRUT(String emitterRUT) { this.emitterRUT = emitterRUT; }
    public String getReceiverRUT() { return receiverRUT; }
    public void setReceiverRUT(String receiverRUT) { this.receiverRUT = receiverRUT; }
    public String getXmlBase64() { return xmlBase64; }
    public void setXmlBase64(String xmlBase64) { this.xmlBase64 = xmlBase64; }
    public Boolean getAssignFolio() { return assignFolio; }
    public void setAssignFolio(Boolean assignFolio) { this.assignFolio = assignFolio; }
    public Integer getTipoDte() { return tipoDte; }
    public void setTipoDte(Integer tipoDte) { this.tipoDte = tipoDte; }
    public Integer getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(Integer puntoVenta) { this.puntoVenta = puntoVenta; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
}
