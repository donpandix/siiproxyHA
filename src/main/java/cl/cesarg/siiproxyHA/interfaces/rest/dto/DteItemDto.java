package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.DteItem;
import java.time.Instant;
import java.util.UUID;

public class DteItemDto {

    public UUID id;
    public Integer nroLinDet;
    public String nmbItem;
    public String dscItem;
    public Double qtyItem;
    public String unmdItem;
    public Double prcItem;
    public Long montoItem;
    public Integer indExe;
    public Instant createdAt;

    public static DteItemDto fromEntity(DteItem item) {
        if (item == null) return null;
        DteItemDto dto = new DteItemDto();
        dto.id = item.getId();
        dto.nroLinDet = item.getNroLinDet();
        dto.nmbItem = item.getNmbItem();
        dto.dscItem = item.getDscItem();
        dto.qtyItem = item.getQtyItem();
        dto.unmdItem = item.getUnmdItem();
        dto.prcItem = item.getPrcItem();
        dto.montoItem = item.getMontoItem();
        dto.indExe = item.getIndExe();
        dto.createdAt = item.getCreatedAt();
        return dto;
    }
}
