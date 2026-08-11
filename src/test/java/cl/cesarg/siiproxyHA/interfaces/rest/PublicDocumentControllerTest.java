package cl.cesarg.siiproxyHA.interfaces.rest;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import cl.cesarg.siiproxyHA.application.service.PublicDocumentService;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentRequest;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentResponse;

class PublicDocumentControllerTest {

    private PublicDocumentService publicDocumentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        publicDocumentService = mock(PublicDocumentService.class);
        TenantContextResolver tenantContextResolver = new TenantContextResolver();
        PublicDocumentController controller =
                new PublicDocumentController(publicDocumentService, tenantContextResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsDocumentSuccessfully() throws Exception {
        UUID documentId = UUID.randomUUID();
        PublicDocumentResponse response = new PublicDocumentResponse();
        response.setDocumentId(documentId.toString());
        response.setType("INVOICE");
        response.setStatus(DocumentStatus.STORED.name());
        response.setFolio(145L);
        response.setCreatedAt(OffsetDateTime.now());

        when(publicDocumentService.createDocument(any(PublicDocumentRequest.class), any(UUID.class), anyString()))
                .thenReturn(new PublicDocumentService.CreateDocumentResult(response, false));

        String payload = """
                {
                  "type": "INVOICE",
                  "issuer": { "rutEnvia": "11111111-1" },
                  "receiver": {
                    "rut": "22222222-2",
                    "businessName": "Empresa Cliente",
                    "businessActivity": "Servicios",
                    "address": "Dirección",
                    "commune": "Viña del Mar",
                    "city": "Viña del Mar",
                    "email": "cliente@example.com"
                  },
                  "issueDate": "2026-08-11",
                  "items": [{
                    "line": 1,
                    "name": "Servicio",
                    "description": "Servicio mensual",
                    "quantity": 1,
                    "unit": "UN",
                    "unitPrice": 10000,
                    "amount": 10000
                  }],
                  "totals": {
                    "net": 10000,
                    "vat": 1900,
                    "total": 11900
                  },
                  "references": []
                }
                """;

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-Tenant-Id", UUID.randomUUID())
                        .header("Idempotency-Key", "sale-84721")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.type").value("INVOICE"))
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void retrievesDocumentForTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        PublicDocumentResponse response = new PublicDocumentResponse();
        response.setDocumentId(documentId.toString());
        response.setType("INVOICE");
        response.setStatus(DocumentStatus.STORED.name());
        response.setFolio(99L);
        response.setCreatedAt(OffsetDateTime.now());

        when(publicDocumentService.getDocument(documentId.toString(), tenantId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.folio").value(99));
    }
}
