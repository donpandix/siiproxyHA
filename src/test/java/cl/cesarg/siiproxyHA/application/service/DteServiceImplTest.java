package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DteServiceImplTest {

    @Mock
    private DocumentoRepositoryPort documentoRepository;
    @Mock
    private StoragePort storagePort;
    @Mock
    private DteRepository dteRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CafService cafService;

    private DteServiceImpl dteService;

    @BeforeEach
    void setUp() {
        dteService = new DteServiceImpl(documentoRepository, storagePort, dteRepository, tenantRepository, cafService);
    }

    @Test
    void ingest_assignsFolioAndGeneratesXmlWhenXmlNotProvided() throws Exception {
        UUID dteId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setRutEmisor("76184688-4");
        tenant.setRazonSocial("Empresa Test");

        Dte persistedDte = new Dte();
        persistedDte.setId(dteId);
        persistedDte.setTenant(tenant);
        persistedDte.setTipoDte(33);
        persistedDte.setFolio(0L);
        persistedDte.setFchEmis(LocalDate.now());
        persistedDte.setRutRecep("11111111-1");
        persistedDte.setRznSocRecep("11111111-1");
        persistedDte.setMntTotal(0L);
        persistedDte.setCreatedAt(Instant.now());
        persistedDte.setUpdatedAt(Instant.now());

        Dte assignedDte = new Dte();
        assignedDte.setId(dteId);
        assignedDte.setTenant(tenant);
        assignedDte.setTipoDte(33);
        assignedDte.setFolio(123L);
        assignedDte.setFchEmis(LocalDate.now());
        assignedDte.setRutRecep("11111111-1");
        assignedDte.setRznSocRecep("11111111-1");
        assignedDte.setMntTotal(0L);

        when(tenantRepository.findByRutEmisor("76184688-4")).thenReturn(Optional.of(tenant));
        when(dteRepository.save(any(Dte.class))).thenReturn(persistedDte);
        when(dteRepository.findById(dteId)).thenReturn(Optional.of(assignedDte));
        when(storagePort.store(any(String.class), any(InputStream.class), anyLong(), eq("application/xml")))
                .thenReturn("dte/object.xml");
        when(documentoRepository.save(any(DocumentMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DteRequest request = new DteRequest();
        request.setEmitterRUT("76184688-4");
        request.setReceiverRUT("11111111-1");
        request.setTipoDte(33);
        request.setPuntoVenta(1);
        request.setRequestId("req-ingest-1");
        request.setAssignedTo("ingest-test");
        request.setAssignFolio(true);

        DocumentMetadata result = dteService.ingest(request);

        verify(cafService).assignFolioToDte(tenantId, dteId, 1, "req-ingest-1", "ingest-test");

        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(storagePort).store(eq("dte/" + dteId + ".xml"), streamCaptor.capture(), anyLong(), eq("application/xml"));
        String generatedXml = new String(streamCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(generatedXml.contains("<Folio>123</Folio>"));

        assertEquals(dteId.toString(), result.getDocumentId());
        assertEquals("123", result.getFolio());
        assertEquals(DocumentStatus.STORED, result.getStatus());
        assertEquals("dte/object.xml", result.getObjectKey());
        assertNotNull(result);
    }
}
