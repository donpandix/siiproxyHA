package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.dto.TenantDto;
import cl.cesarg.siiproxyHA.application.service.TenantService;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantControllerTest {

    private TenantService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TenantService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TenantController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void acceptsPartialTenantUpdate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant updated = new Tenant();
        updated.setId(tenantId);
        updated.setTenantCode("TENANT-ORIGINAL");
        updated.setRutEmisor("76184688-4");
        updated.setRazonSocial("Empresa Original");
        updated.setFchResol(LocalDate.of(2020, 8, 14));
        updated.setNroResol(80);
        when(service.update(eq(tenantId), any(TenantDto.class)))
                .thenReturn(Optional.of(updated));

        mockMvc.perform(put("/api/v1/tenants/{id}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acteco": "726000",
                                  "email": "cesar@cesarg.cl",
                                  "fchResol": "2020-08-14",
                                  "nroResol": 80,
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value("TENANT-ORIGINAL"))
                .andExpect(jsonPath("$.fchResol").value("2020-08-14"))
                .andExpect(jsonPath("$.nroResol").value(80));

        verify(service).update(eq(tenantId), any(TenantDto.class));
    }

    @Test
    void rejectsResolutionNumberOutsideSiiRange() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/tenants/{id}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nroResol": 1000000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.nroResol").exists());

        verify(service, never()).update(eq(tenantId), any(TenantDto.class));
    }

    @Test
    void rejectsResolutionDateOutsideIsoFormat() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/tenants/{id}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fchResol": "14-08-2020"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).update(eq(tenantId), any(TenantDto.class));
    }
}
