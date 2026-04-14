package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.dto.ReceptorDto;
import cl.cesarg.siiproxyHA.application.service.ReceptorService;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ReceptorController {

    private final ReceptorService receptorService;

    public ReceptorController(ReceptorService receptorService) {
        this.receptorService = receptorService;
    }

    @PostMapping("/api/v1/tenants/{tenantId}/receptores")
    public ResponseEntity<Receptor> create(@PathVariable("tenantId") UUID tenantId,
                                           @Valid @RequestBody ReceptorDto dto) {
        Receptor created = receptorService.create(tenantId, dto);
        return ResponseEntity.created(URI.create("/api/v1/receptores/" + created.getId())).body(created);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/receptores")
    public ResponseEntity<List<Receptor>> listByTenant(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(receptorService.listByTenant(tenantId));
    }

    @GetMapping("/api/v1/receptores/{id}")
    public ResponseEntity<Receptor> get(@PathVariable("id") UUID id) {
        return receptorService.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/receptores/{id}")
    public ResponseEntity<Receptor> update(@PathVariable("id") UUID id, @Valid @RequestBody ReceptorDto dto) {
        return receptorService.update(id, dto).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/v1/receptores/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        receptorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
