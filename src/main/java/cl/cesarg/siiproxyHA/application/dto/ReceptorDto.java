package cl.cesarg.siiproxyHA.application.dto;

import java.util.UUID;

public class ReceptorDto {
    private UUID id;
    private String rutReceptor;
    private String razonSocial;
    private String giro;
    private String email;
    private String telefono;
    private String direccion;
    private String comuna;
    private String ciudad;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getRutReceptor() { return rutReceptor; }
    public void setRutReceptor(String rutReceptor) { this.rutReceptor = rutReceptor; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getGiro() { return giro; }
    public void setGiro(String giro) { this.giro = giro; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
}
