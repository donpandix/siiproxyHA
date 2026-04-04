package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.application.service.DteService;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dte")
public class DteController {

    private final DteService dteService;

    public DteController(DteService dteService) {
        this.dteService = dteService;
    }

    @PostMapping
    public ResponseEntity<DocumentMetadata> ingest(@Valid @RequestBody DteRequest request) throws Exception {
        DocumentMetadata meta = dteService.ingest(request);
        return ResponseEntity.status(201).body(meta);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DocumentMetadata> status(@PathVariable("id") String id) throws Exception {
        DocumentMetadata meta = dteService.getStatus(id);
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<?> xml(@PathVariable("id") String id,
                                 @RequestParam(name = "presigned", required = false, defaultValue = "false") boolean presigned,
                                 @RequestParam(name = "expiryMinutes", required = false, defaultValue = "60") int expiryMinutes
    ) throws Exception {
        var resp = dteService.getXml(id, presigned, expiryMinutes);
        return ResponseEntity.ok(resp);
    }
}
