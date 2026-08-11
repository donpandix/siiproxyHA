package cl.cesarg.siiproxyHA.interfaces.rest.dto;

public class PublicDocumentSiiStatus {

    private Long trackId;
    private String status;
    private String message;

    public Long getTrackId() { return trackId; }
    public void setTrackId(Long trackId) { this.trackId = trackId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
