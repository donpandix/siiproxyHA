package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.service.UserCertificateService;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
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
        return ResponseEntity.created(location).body(entity);
    }
}
