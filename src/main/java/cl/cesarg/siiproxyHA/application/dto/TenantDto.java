package cl.cesarg.siiproxyHA.application.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TenantDto {
    private UUID id;
    private String tenantCode;
    private String rutEmisor;
    private String razonSocial;
    private String giro;
    private String acteco;
    private String direccion;
    private String comuna;
    private String ciudad;
    private String email;
    private boolean active = true;
    private List<ReceptorDto> receptores = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getRutEmisor() { return rutEmisor; }
    public void setRutEmisor(String rutEmisor) { this.rutEmisor = rutEmisor; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getGiro() { return giro; }
    public void setGiro(String giro) { this.giro = giro; }
    public String getActeco() { return acteco; }
    public void setActeco(String acteco) { this.acteco = acteco; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<ReceptorDto> getReceptores() { return receptores; }
    public void setReceptores(List<ReceptorDto> receptores) { this.receptores = receptores; }
}
