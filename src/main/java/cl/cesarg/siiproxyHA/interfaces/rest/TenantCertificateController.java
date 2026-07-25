package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.service.UserCertificateService;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.UserCertificateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/certificates")
public class TenantCertificateController {

    private final UserCertificateService service;

    public TenantCertificateController(UserCertificateService service) {
        this.service = service;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadCertificate(@PathVariable UUID tenantId,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam("rutUsuario") String rutUsuario,
                                               @RequestParam(value = "nombreUsuario", required = false) String nombreUsuario,
                                               @RequestParam(value = "password", required = false) String password,
                                               @RequestParam(value = "isDefault", required = false, defaultValue = "false") boolean isDefault,
                                               @RequestParam(value = "createdBy", required = false) String createdBy
    ) throws Exception {

        UserCertificateEntity entity = service.uploadCertificate(
                tenantId,
                rutUsuario,
                nombreUsuario,
                createdBy,
                file.getOriginalFilename(),
                file.getInputStream(),
                file.getSize(),
                file.getContentType(),
                password,
                isDefault
        );

        URI location = URI.create(String.format("/api/tenants/%s/certificates/%s", tenantId, entity.getId()));
        return ResponseEntity.created(location).body(UserCertificateDto.fromEntity(entity));
    }

    @GetMapping
    public ResponseEntity<List<UserCertificateDto>> listCertificates(@PathVariable UUID tenantId) {
        List<UserCertificateDto> certificates = service.listCertificates(tenantId).stream()
                .map(UserCertificateDto::fromEntity)
                .toList();

        return ResponseEntity.ok(certificates);
    }

    @GetMapping("/{certificateId}")
    public ResponseEntity<UserCertificateDto> getCertificate(@PathVariable UUID tenantId,
                                                             @PathVariable UUID certificateId) {
        UserCertificateEntity certificate = service.getCertificate(tenantId, certificateId);
        return ResponseEntity.ok(UserCertificateDto.fromEntity(certificate));
    }

    @DeleteMapping("/{certificateId}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable UUID tenantId,
                                                  @PathVariable UUID certificateId) {
        service.deleteCertificate(tenantId, certificateId);
        return ResponseEntity.noContent().build();
    }
}
