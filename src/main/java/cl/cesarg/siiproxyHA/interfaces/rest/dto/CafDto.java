package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.Caf;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CafDto {
    public UUID id;
    public UUID tenantId;
    public Integer tipoDte;
    public Integer puntoVenta;
    public Long folioDesde;
    public Long folioHasta;
    public String rutEmisor;
    public LocalDate fchAutorizacion;
    public Instant createdAt;
    public boolean active;

    public static CafDto fromEntity(Caf entity) {
        CafDto dto = new CafDto();
        dto.id = entity.getId();
        dto.tenantId = entity.getTenant() != null ? entity.getTenant().getId() : null;
        dto.tipoDte = entity.getTipoDte();
        dto.puntoVenta = entity.getPuntoVenta();
        dto.folioDesde = entity.getFolioDesde();
        dto.folioHasta = entity.getFolioHasta();
        dto.rutEmisor = entity.getRutEmisor();
        dto.fchAutorizacion = entity.getFchAutorizacion();
        dto.createdAt = entity.getCreatedAt();
        dto.active = entity.isActive();
        return dto;
    }
}
