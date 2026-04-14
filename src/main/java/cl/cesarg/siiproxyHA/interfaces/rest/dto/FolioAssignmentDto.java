package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;

import java.time.Instant;
import java.util.UUID;

public class FolioAssignmentDto {
    public UUID id;
    public UUID tenantId;
    public Integer tipoDte;
    public Integer puntoVenta;
    public Long folio;
    public String status;
    public String requestId;
    public String assignedTo;
    public Instant assignedAt;
    public UUID dteId;
    public UUID folioPoolId;
    public String note;

    public static FolioAssignmentDto fromEntity(FolioAssignment entity) {
        FolioAssignmentDto dto = new FolioAssignmentDto();
        dto.id = entity.getId();
        dto.tenantId = entity.getTenant() != null ? entity.getTenant().getId() : null;
        dto.tipoDte = entity.getTipoDte();
        dto.puntoVenta = entity.getPuntoVenta();
        dto.folio = entity.getFolio();
        dto.status = entity.getStatus();
        dto.requestId = entity.getRequestId();
        dto.assignedTo = entity.getAssignedTo();
        dto.assignedAt = entity.getAssignedAt();
        dto.dteId = entity.getDte() != null ? entity.getDte().getId() : null;
        dto.folioPoolId = entity.getFolioPool() != null ? entity.getFolioPool().getId() : null;
        dto.note = entity.getNote();
        return dto;
    }
}
