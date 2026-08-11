package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.service.CafService;
import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CafControllerTest {

    private CafService cafService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cafService = mock(CafService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CafController(cafService)).build();
    }

    @Test
    void listsCafsWithoutSerializingTenantOrInternalStorageFields() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID cafId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        Caf caf = new Caf();
        caf.setId(cafId);
        caf.setTenant(tenant);
        caf.setTipoDte(33);
        caf.setPuntoVenta(1);
        caf.setFolioDesde(1L);
        caf.setFolioHasta(100L);
        caf.setCafPath("caf/private/authorization.xml");
        caf.setCafSha256("secret-storage-hash");
        caf.setRutEmisor("10438332-7");
        caf.setFchAutorizacion(LocalDate.of(2026, 8, 10));
        caf.setCreatedAt(Instant.parse("2026-08-10T22:07:23Z"));
        caf.setActive(true);
        when(cafService.list()).thenReturn(List.of(caf));

        mockMvc.perform(get("/api/v1/caf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cafId.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$[0].tipoDte").value(33))
                .andExpect(jsonPath("$[0].folioDesde").value(1))
                .andExpect(jsonPath("$[0].folioHasta").value(100))
                .andExpect(jsonPath("$[0].tenant").doesNotExist())
                .andExpect(jsonPath("$[0].cafPath").doesNotExist())
                .andExpect(jsonPath("$[0].cafSha256").doesNotExist())
                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
    }
}
