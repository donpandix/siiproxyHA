package cl.cesarg.siiproxyHA.domain.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Column(name = "rut_emisor", nullable = false, length = 12)
    private String rutEmisor;

    @Column(name = "razon_social", nullable = false, length = 100)
    private String razonSocial;

    @Column(name = "giro", length = 80)
    private String giro;

    @Column(name = "acteco", length = 6)
    private String acteco;

    @Column(name = "dir_tenant", length = 70)
    private String direccion;

    @Column(name = "cmna_tenant", length = 20)
    private String comuna;

    @Column(name = "ciudad_tenant", length = 20)
    private String ciudad;

    @Column(name = "email", length = 80)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Receptor> receptores = new ArrayList<>();

    public Tenant() {}

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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<Receptor> getReceptores() { return receptores; }
    public void setReceptores(List<Receptor> receptores) { this.receptores = receptores; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
