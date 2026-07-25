package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
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
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    @Mock
    private DteXmlAssemblyService xmlAssembly;

    private DteServiceImpl dteService;
    private AtomicReference<DocumentMetadata> metadata;

    @BeforeEach
    void setUp() {
        metadata = new AtomicReference<>();
        when(documentoRepository.createIfAbsent(any(DocumentMetadata.class))).thenAnswer(invocation -> {
            metadata.compareAndSet(null, invocation.getArgument(0));
            return metadata.get();
        });
        when(documentoRepository.tryClaimStore(any(String.class), any(OffsetDateTime.class))).thenAnswer(invocation -> {
            DocumentMetadata current = metadata.get();
            current.setStatus(DocumentStatus.PENDING_STORE);
            current.setAttemptCount((current.getAttemptCount() == null ? 0 : current.getAttemptCount()) + 1);
            return true;
        });
        when(documentoRepository.findByDocumentId(any(String.class)))
                .thenAnswer(invocation -> Optional.ofNullable(metadata.get()));
        when(documentoRepository.save(any(DocumentMetadata.class))).thenAnswer(invocation -> {
            DocumentMetadata current = invocation.getArgument(0);
            metadata.set(current);
            return current;
        });
        dteService = new DteServiceImpl(
                documentoRepository,
                storagePort,
                dteRepository,
                tenantRepository,
                cafService,
                xmlAssembly
        );
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

        when(tenantRepository.findByRutEmisor("76184688-4")).thenReturn(Optional.of(tenant));
        when(dteRepository.save(any(Dte.class))).thenReturn(persistedDte);
        FolioAssignment assignment = new FolioAssignment();
        assignment.setFolio(123L);
        when(cafService.assignFolioToDte(tenantId, dteId, 1, "req-ingest-1", "ingest-test"))
                .thenReturn(assignment);
        when(storagePort.store(any(String.class), any(InputStream.class), anyLong(), eq("application/xml")))
                .thenReturn("dte/object.xml");
        byte[] builtXml = ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                + "<EnvioDTE><Folio>123</Folio>"
                + "<FRMT algoritmo=\"SHA1withRSA\">signed</FRMT>"
                + "<TmstFirma>2026-07-24T12:30:45</TmstFirma></EnvioDTE>")
                .getBytes(StandardCharsets.ISO_8859_1);
        when(xmlAssembly.build(persistedDte)).thenReturn(
                new DteXmlBuilderPort.BuiltDteXml(
                        builtXml,
                        "DTE-123",
                        "SetDTE-" + dteId,
                        "ISO-8859-1"
                )
        );

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
        verify(storagePort).store(
                eq("dte/" + LocalDate.now().getYear() + "/"
                        + "%02d".formatted(LocalDate.now().getMonthValue())
                        + "/" + dteId + "-123.xml"),
                streamCaptor.capture(),
                anyLong(),
                eq("application/xml")
        );
        String generatedXml = new String(
                streamCaptor.getValue().readAllBytes(),
                StandardCharsets.ISO_8859_1
        );
        assertTrue(generatedXml.contains("<Folio>123</Folio>"));
        assertTrue(generatedXml.contains("<FRMT algoritmo=\"SHA1withRSA\">signed</FRMT>"));
        assertTrue(generatedXml.contains("<TmstFirma>2026-07-24T12:30:45</TmstFirma>"));
        assertTrue(generatedXml.contains("encoding=\"ISO-8859-1\""));
        verify(xmlAssembly).build(persistedDte);

        assertEquals(dteId.toString(), result.getDocumentId());
        assertEquals("123", result.getFolio());
        assertEquals(DocumentStatus.STORED, result.getStatus());
        assertEquals("dte/object.xml", result.getObjectKey());
        assertNotNull(result);
    }
}
