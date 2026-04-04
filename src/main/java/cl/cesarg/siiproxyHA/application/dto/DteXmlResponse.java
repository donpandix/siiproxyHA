package cl.cesarg.siiproxyHA.application.dto;

public class DteXmlResponse {
    private String documentId;
    private String xmlBase64;
    private String presignedUrl;

    public DteXmlResponse() {}

    public DteXmlResponse(String documentId, String xmlBase64, String presignedUrl) {
        this.documentId = documentId;
        this.xmlBase64 = xmlBase64;
        this.presignedUrl = presignedUrl;
    }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getXmlBase64() { return xmlBase64; }
    public void setXmlBase64(String xmlBase64) { this.xmlBase64 = xmlBase64; }
    public String getPresignedUrl() { return presignedUrl; }
    public void setPresignedUrl(String presignedUrl) { this.presignedUrl = presignedUrl; }
}
