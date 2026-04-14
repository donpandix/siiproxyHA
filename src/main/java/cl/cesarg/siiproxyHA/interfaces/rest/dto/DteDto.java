package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DteDto {

    public UUID id;
    public UUID tenantId;
    public String tenantCode;
    public TenantDto tenant;
    public Integer tipoDte;
    public Long folio;
    public LocalDate fchEmis;
    public UUID receptorId;
    public String rutRecep;
    public String rznSocRecep;
    public ReceptorDto receptor;
    public List<DteReferenceDto> references;
    public List<DteItemDto> items;
    public Long mntNeto;
    public Long iva;
    public Long mntTotal;
    public Instant createdAt;
    public Instant updatedAt;

    public static DteDto fromEntity(Dte dte) {
        if (dte == null) return null;
        DteDto dto = new DteDto();
        dto.id = dte.getId();
        try {
            if (dte.getTenant() != null) {
                dto.tenantId = dte.getTenant().getId();
                dto.tenantCode = dte.getTenant().getTenantCode();
            }
        } catch (Exception ignored) {}
        dto.tipoDte = dte.getTipoDte();
        dto.folio = dte.getFolio();
        dto.fchEmis = dte.getFchEmis();
        try {
            if (dte.getReceptor() != null) {
                dto.receptorId = dte.getReceptor().getId();
                dto.receptor = ReceptorDto.fromEntity(dte.getReceptor());
            }
        } catch (Exception ignored) {}
        dto.rutRecep = dte.getRutRecep();
        dto.rznSocRecep = dte.getRznSocRecep();
        dto.mntNeto = dte.getMntNeto();
        dto.iva = dte.getIva();
        dto.mntTotal = dte.getMntTotal();
        dto.createdAt = dte.getCreatedAt();
        dto.updatedAt = dte.getUpdatedAt();
        try {
            if (dte.getReferences() != null) dto.references = dte.getReferences().stream().map(DteReferenceDto::fromEntity).toList();
        } catch (Exception ignored) {}
        try {
            if (dte.getItems() != null) dto.items = dte.getItems().stream().map(DteItemDto::fromEntity).toList();
        } catch (Exception ignored) {}
        try {
            if (dte.getTenant() != null) dto.tenant = TenantDto.fromEntity(dte.getTenant());
        } catch (Exception ignored) {}
        return dto;
    }
}
