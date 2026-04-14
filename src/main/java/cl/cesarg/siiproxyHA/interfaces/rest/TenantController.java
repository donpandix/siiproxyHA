package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.dto.TenantDto;
import cl.cesarg.siiproxyHA.application.service.TenantService;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<Tenant> create(@Valid @RequestBody TenantDto dto) {
        Tenant created = tenantService.create(dto);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + created.getId())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> list() {
        return ResponseEntity.ok(tenantService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tenant> get(@PathVariable("id") UUID id) {
        return tenantService.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tenant> update(@PathVariable("id") UUID id, @Valid @RequestBody TenantDto dto) {
        return tenantService.update(id, dto).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
