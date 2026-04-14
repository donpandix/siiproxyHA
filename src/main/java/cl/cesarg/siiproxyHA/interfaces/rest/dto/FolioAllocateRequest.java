package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import java.util.UUID;

public class FolioAllocateRequest {
    public UUID tenantId;
    public Integer tipoDte;
    public Integer puntoVenta;
    public String requestId;
    public String assignedTo;
}
