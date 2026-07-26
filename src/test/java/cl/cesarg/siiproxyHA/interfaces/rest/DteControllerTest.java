package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.service.DteIngestService;
import cl.cesarg.siiproxyHA.application.service.DteService;
import cl.cesarg.siiproxyHA.application.service.DteXmlRegenerationService;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DteControllerTest {

    private DteXmlRegenerationService regenerationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DteService dteService = mock(DteService.class);
        DteIngestService ingestService = mock(DteIngestService.class);
        regenerationService = mock(DteXmlRegenerationService.class);
        DteController controller = new DteController(
                dteService,
                ingestService,
                regenerationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void regeneratesSignedXmlForExistingDte() throws Exception {
        UUID documentId = UUID.randomUUID();
        DocumentMetadata metadata = new DocumentMetadata(
                documentId.toString(),
                DocumentStatus.STORED
        );
        metadata.setFolio("182");
        metadata.setAttemptCount(2);
        metadata.setObjectKey("dte/2026/07/" + documentId + "-182.xml");
        metadata.setSha256("a".repeat(64));
        metadata.setSizeBytes(10_650L);
        when(regenerationService.regenerate(documentId.toString()))
                .thenReturn(metadata);

        mockMvc.perform(post(
                        "/api/v1/dte/{id}/xml/regenerate",
                        documentId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.status").value("STORED"))
                .andExpect(jsonPath("$.attemptCount").value(2))
                .andExpect(jsonPath("$.sha256").value("a".repeat(64)));
    }
}
