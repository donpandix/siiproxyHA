package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.application.service.UserCertificateService;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantCertificateControllerTest {

    private UserCertificateService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(UserCertificateService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TenantCertificateController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsCertificatesWithoutSensitiveFields() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserCertificateEntity certificate = new UserCertificateEntity();
        certificate.setId(UUID.randomUUID());
        certificate.setTenantId(tenantId);
        certificate.setRutUsuario("10438332-7");
        certificate.setCertSubjectRut("10438332-7");
        certificate.setStatus("ACTIVE");
        certificate.setDefault(true);
        certificate.setCreatedAt(OffsetDateTime.parse("2026-07-22T03:43:29Z"));
        certificate.setCertificatePath("tenants/private/certificate.pfx");
        certificate.setCertificateHash("secret-hash");
        certificate.setEncryptedPassword("encrypted-secret");
        certificate.setEncryptionIv("secret-iv");
        when(service.listCertificates(tenantId)).thenReturn(List.of(certificate));

        mockMvc.perform(get("/api/tenants/{tenantId}/certificates", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(certificate.getId().toString()))
                .andExpect(jsonPath("$[0].rutUsuario").value("10438332-7"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].certificatePath").doesNotExist())
                .andExpect(jsonPath("$[0].certificateHash").doesNotExist())
                .andExpect(jsonPath("$[0].encryptedPassword").doesNotExist())
                .andExpect(jsonPath("$[0].encryptionIv").doesNotExist())
                .andExpect(jsonPath("$[0].encryptionAlgorithm").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(service.listCertificates(tenantId))
                .thenThrow(new ResourceNotFoundException("Tenant not found: " + tenantId));

        mockMvc.perform(get("/api/tenants/{tenantId}/certificates", tenantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void getsCertificateByIdWithoutSensitiveFields() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        UserCertificateEntity certificate = new UserCertificateEntity();
        certificate.setId(certificateId);
        certificate.setTenantId(tenantId);
        certificate.setRutUsuario("10438332-7");
        certificate.setCertificatePath("tenants/private/certificate.pfx");
        certificate.setEncryptedPassword("encrypted-secret");
        when(service.getCertificate(tenantId, certificateId)).thenReturn(certificate);

        mockMvc.perform(get("/api/tenants/{tenantId}/certificates/{certificateId}",
                        tenantId, certificateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(certificateId.toString()))
                .andExpect(jsonPath("$.rutUsuario").value("10438332-7"))
                .andExpect(jsonPath("$.certificatePath").doesNotExist())
                .andExpect(jsonPath("$.encryptedPassword").doesNotExist());
    }

    @Test
    void deletesCertificateAndReturnsNoContent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();

        mockMvc.perform(delete("/api/tenants/{tenantId}/certificates/{certificateId}",
                        tenantId, certificateId))
                .andExpect(status().isNoContent());

        verify(service).deleteCertificate(tenantId, certificateId);
    }
}
