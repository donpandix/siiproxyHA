package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.DteDto;
import cl.cesarg.siiproxyHA.application.service.DteCrudService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dte/records")
public class DteCrudController {

    private final DteCrudService dteCrudService;

    public DteCrudController(DteCrudService dteCrudService) {
        this.dteCrudService = dteCrudService;
    }

    @PostMapping
    public ResponseEntity<Dte> create(@RequestBody Dte dte) {
        Dte created = dteCrudService.create(dte);
        return ResponseEntity.created(URI.create("/api/v1/dte/records/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DteDto> getById(@PathVariable UUID id,
                                          @RequestParam(required = true) UUID tenantId) {
        Optional<Dte> dte = dteCrudService.findByIdAndTenantId(id, tenantId);
        return dte.map(d -> ResponseEntity.ok(DteDto.fromEntity(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<DteDto>> listByTenant(@RequestParam(required = false) UUID tenantId) {
        List<Dte> list = dteCrudService.findAllByTenantId(tenantId);
        List<DteDto> dtos = list.stream().map(DteDto::fromEntity).toList();
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Dte> update(@PathVariable UUID id,
                                      @RequestParam(required = true) UUID tenantId,
                                      @RequestBody Dte dte) {
        Optional<Dte> updated = dteCrudService.updateForTenant(id, tenantId, dte);
        return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestParam(required = true) UUID tenantId) {
        boolean deleted = dteCrudService.deleteForTenant(id, tenantId);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }
}
