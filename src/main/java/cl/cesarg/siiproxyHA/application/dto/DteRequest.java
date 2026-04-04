package cl.cesarg.siiproxyHA.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DteRequest {

    private String documentId;

    @NotBlank
    private String emitterRUT;

    @NotBlank
    private String receiverRUT;

    // Optional base64 XML payload
    private String xmlBase64;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getEmitterRUT() { return emitterRUT; }
    public void setEmitterRUT(String emitterRUT) { this.emitterRUT = emitterRUT; }
    public String getReceiverRUT() { return receiverRUT; }
    public void setReceiverRUT(String receiverRUT) { this.receiverRUT = receiverRUT; }
    public String getXmlBase64() { return xmlBase64; }
    public void setXmlBase64(String xmlBase64) { this.xmlBase64 = xmlBase64; }
}
