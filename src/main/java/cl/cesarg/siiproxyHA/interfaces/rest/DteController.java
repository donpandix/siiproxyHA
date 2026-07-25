package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.dto.DteIngestPayload;
import cl.cesarg.siiproxyHA.application.service.DteIngestService;
import cl.cesarg.siiproxyHA.application.service.DteService;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/dte")
public class DteController {

    private final DteService dteService;
    private final DteIngestService dteIngestService;

    public DteController(DteService dteService,
                         DteIngestService dteIngestService) {
        this.dteService = dteService;
        this.dteIngestService = dteIngestService;
    }

    @PostMapping
    public ResponseEntity<DocumentMetadata> ingest(@Valid @RequestBody DteIngestPayload payload) throws Exception {
        return ResponseEntity.status(201).body(dteIngestService.ingest(payload));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DocumentMetadata> status(@PathVariable("id") String id) throws Exception {
        DocumentMetadata meta = dteService.getStatus(id);
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<?> xml(@PathVariable("id") String id,
                                 @RequestParam(name = "presigned", required = false, defaultValue = "false") boolean presigned,
                                 @RequestParam(name = "expiryMinutes", required = false, defaultValue = "60") int expiryMinutes,
                                 @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept
    ) throws Exception {
        var resp = dteService.getXml(id, presigned, expiryMinutes);

        if (accept != null
                && accept.contains(MediaType.APPLICATION_XML_VALUE)
                && resp.getXmlBase64() != null
                && !resp.getXmlBase64().isBlank()) {
            String xml = new String(Base64.getDecoder().decode(resp.getXmlBase64()), StandardCharsets.UTF_8);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
        }

        return ResponseEntity.ok(resp);
    }
}
