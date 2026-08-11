package cl.cesarg.siiproxyHA.interfaces.rest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.cesarg.siiproxyHA.application.service.PublicDocumentService;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentRequest;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class PublicDocumentController {

    private final PublicDocumentService publicDocumentService;
    private final TenantContextResolver tenantContextResolver;

    public PublicDocumentController(PublicDocumentService publicDocumentService,
                                   TenantContextResolver tenantContextResolver) {
        this.publicDocumentService = publicDocumentService;
        this.tenantContextResolver = tenantContextResolver;
    }

    @PostMapping("/documents")
    public ResponseEntity<PublicDocumentResponse> create(
            @Valid @RequestBody PublicDocumentRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) throws Exception {
        UUID tenantId = tenantContextResolver.resolve(tenantHeader);
        PublicDocumentService.CreateDocumentResult result =
                publicDocumentService.createDocument(request, tenantId, idempotencyKey);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.response());
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<PublicDocumentResponse> get(
            @PathVariable String documentId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader
    ) {
        UUID tenantId = tenantContextResolver.resolve(tenantHeader);
        return ResponseEntity.ok(publicDocumentService.getDocument(documentId, tenantId));
    }

    @GetMapping("/documents/{documentId}/status")
    public ResponseEntity<PublicDocumentResponse> getStatus(
            @PathVariable String documentId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader
    ) {
        UUID tenantId = tenantContextResolver.resolve(tenantHeader);
        return ResponseEntity.ok(publicDocumentService.getDocumentStatus(documentId, tenantId));
    }
}
