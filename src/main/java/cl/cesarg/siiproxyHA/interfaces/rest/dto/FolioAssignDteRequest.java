package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import java.util.UUID;

public class FolioAssignDteRequest {
    public UUID tenantId;
    public UUID dteId;
    public Integer puntoVenta;
    public String requestId;
    public String assignedTo;
}
