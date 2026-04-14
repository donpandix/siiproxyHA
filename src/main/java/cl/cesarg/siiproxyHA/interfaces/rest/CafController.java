package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.service.CafService;
import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.FolioAllocateRequest;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.FolioAssignDteRequest;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.FolioAssignmentDto;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.FolioReleaseRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caf")
public class CafController {

    private final CafService cafService;

    public CafController(CafService cafService) {
        this.cafService = cafService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Caf> upload(@RequestParam("tenantId") UUID tenantId,
                                      @RequestParam(value = "puntoVenta", defaultValue = "1") Integer puntoVenta,
                                      @RequestParam("file") MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        Caf created = cafService.create(tenantId, puntoVenta, bytes, file.getOriginalFilename());
        return ResponseEntity.created(URI.create("/api/v1/caf/" + created.getId())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Caf>> list() { return ResponseEntity.ok(cafService.list()); }

    @GetMapping("/{id}")
    public ResponseEntity<Caf> get(@PathVariable("id") UUID id) {
        return cafService.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        cafService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") UUID id) throws Exception {
        var cafOpt = cafService.get(id);
        if (cafOpt.isEmpty()) return ResponseEntity.notFound().build();
        var caf = cafOpt.get();
        byte[] data = cafService.downloadFile(caf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename= caf-" + caf.getId() + ".xml")
                .contentType(MediaType.APPLICATION_XML)
                .body(data);
    }

    @PostMapping("/folios/allocate")
    public ResponseEntity<FolioAssignmentDto> allocate(@RequestBody FolioAllocateRequest request) {
        var assignment = cafService.allocateNextFolio(
                request.tenantId,
                request.tipoDte,
                request.puntoVenta,
                request.requestId,
                request.assignedTo
        );
        return ResponseEntity.ok(FolioAssignmentDto.fromEntity(assignment));
    }

    @PostMapping("/folios/assign-to-dte")
    public ResponseEntity<FolioAssignmentDto> assignToDte(@RequestBody FolioAssignDteRequest request) {
        var assignment = cafService.assignFolioToDte(
                request.tenantId,
                request.dteId,
                request.puntoVenta,
                request.requestId,
                request.assignedTo
        );
        return ResponseEntity.ok(FolioAssignmentDto.fromEntity(assignment));
    }

    @PostMapping("/folios/{assignmentId}/release")
    public ResponseEntity<FolioAssignmentDto> release(@PathVariable UUID assignmentId,
                                                      @RequestBody FolioReleaseRequest request) {
        var assignment = cafService.releaseFolio(request.tenantId, assignmentId, request.note);
        return ResponseEntity.ok(FolioAssignmentDto.fromEntity(assignment));
    }

    @GetMapping("/folios/status")
    public ResponseEntity<CafService.FolioStatus> status(@RequestParam UUID tenantId,
                                                         @RequestParam Integer tipoDte,
                                                         @RequestParam(defaultValue = "1") Integer puntoVenta) {
        return ResponseEntity.ok(cafService.getFolioStatus(tenantId, tipoDte, puntoVenta));
    }
}
