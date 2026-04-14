package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.DteReference;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class DteReferenceDto {

    public UUID id;
    public Integer nroLinRef;
    public String tpoDocRef;
    public String folioRef;
    public LocalDate fchRef;
    public String codRef;
    public String razonRef;
    public Instant createdAt;

    public static DteReferenceDto fromEntity(DteReference ref) {
        if (ref == null) return null;
        DteReferenceDto dto = new DteReferenceDto();
        dto.id = ref.getId();
        dto.nroLinRef = ref.getNroLinRef();
        dto.tpoDocRef = ref.getTpoDocRef();
        dto.folioRef = ref.getFolioRef();
        dto.fchRef = ref.getFchRef();
        dto.codRef = ref.getCodRef();
        dto.razonRef = ref.getRazonRef();
        dto.createdAt = ref.getCreatedAt();
        return dto;
    }
}
