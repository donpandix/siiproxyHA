package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.domain.model.Receptor;
import java.time.Instant;
import java.util.UUID;

public class ReceptorDto {

    public UUID id;
    public String rutReceptor;
    public String razonSocial;
    public String giro;
    public String email;
    public String telefono;
    public String direccion;
    public String comuna;
    public String ciudad;
    public Instant createdAt;

    public static ReceptorDto fromEntity(Receptor r) {
        if (r == null) return null;
        ReceptorDto dto = new ReceptorDto();
        dto.id = r.getId();
        dto.rutReceptor = r.getRutReceptor();
        dto.razonSocial = r.getRazonSocial();
        dto.giro = r.getGiro();
        dto.email = r.getEmail();
        dto.telefono = r.getTelefono();
        dto.direccion = r.getDireccion();
        dto.comuna = r.getComuna();
        dto.ciudad = r.getCiudad();
        dto.createdAt = r.getCreatedAt();
        return dto;
    }
}
