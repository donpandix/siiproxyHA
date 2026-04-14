package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.Tenant;
import java.time.Instant;
import java.util.UUID;

public class TenantDto {

    public UUID id;
    public String tenantCode;
    public String rutEmisor;
    public String razonSocial;
    public String giro;
    public String acteco;
    public String direccion;
    public String comuna;
    public String ciudad;
    public String email;
    public Instant createdAt;

    public static TenantDto fromEntity(Tenant t) {
        if (t == null) return null;
        TenantDto dto = new TenantDto();
        dto.id = t.getId();
        dto.tenantCode = t.getTenantCode();
        dto.rutEmisor = t.getRutEmisor();
        dto.razonSocial = t.getRazonSocial();
        dto.giro = t.getGiro();
        dto.acteco = t.getActeco();
        dto.direccion = t.getDireccion();
        dto.comuna = t.getComuna();
        dto.ciudad = t.getCiudad();
        dto.email = t.getEmail();
        dto.createdAt = t.getCreatedAt();
        return dto;
    }
}
