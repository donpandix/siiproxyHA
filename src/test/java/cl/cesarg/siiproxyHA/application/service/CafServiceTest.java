package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.FolioPool;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.CafParserPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.FolioAssignmentRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.FolioPoolRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CafServiceTest {

    private CafRepository cafRepository;
    private TenantRepository tenantRepository;
    private FolioPoolRepository folioPoolRepository;
    private FolioAssignmentRepository folioAssignmentRepository;
    private DteRepository dteRepository;
    private StoragePort storagePort;
    private CafParserPort cafParser;
    private CafService cafService;

    @BeforeEach
    void setUp() {
        cafRepository = mock(CafRepository.class);
        tenantRepository = mock(TenantRepository.class);
        folioPoolRepository = mock(FolioPoolRepository.class);
        folioAssignmentRepository = mock(FolioAssignmentRepository.class);
        dteRepository = mock(DteRepository.class);
        storagePort = mock(StoragePort.class);
        cafParser = mock(CafParserPort.class);

        cafService = new CafService(
                cafRepository,
                tenantRepository,
                folioPoolRepository,
                folioAssignmentRepository,
                dteRepository,
                storagePort,
                cafParser
        );
    }

    @Test
    void createUsesParsedMetadataAndSanitizesStorageFilename() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setRutEmisor("10.438.332-7");
        byte[] authorizationXml = "<AUTORIZACION/>".getBytes(StandardCharsets.UTF_8);
        byte[] publicCafXml = "<CAF/>".getBytes(StandardCharsets.UTF_8);
        CafParserPort.ParsedCaf parsed = new CafParserPort.ParsedCaf(
                "10438332-7",
                33,
                100,
                110,
                LocalDate.of(2026, 1, 1),
                publicCafXml,
                true
        );

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(cafParser.parse(authorizationXml)).thenReturn(parsed);
        when(cafRepository.save(any(Caf.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folioPoolRepository.save(any(FolioPool.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Caf created = cafService.create(
                tenantId,
                2,
                authorizationXml,
                "../../caf autorizado.xml"
        );

        assertEquals(33, created.getTipoDte());
        assertEquals(2, created.getPuntoVenta());
        assertEquals(100, created.getFolioDesde());
        assertEquals(110, created.getFolioHasta());
        assertEquals("10438332-7", created.getRutEmisor());
        assertEquals(LocalDate.of(2026, 1, 1), created.getFchAutorizacion());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storagePort).store(
                keyCaptor.capture(),
                any(),
                eq((long) authorizationXml.length),
                eq("application/xml")
        );
        assertTrue(keyCaptor.getValue().endsWith("-caf_autorizado.xml"));
    }

    @Test
    void createRejectsCafForAnotherIssuerBeforeStorage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setRutEmisor("10438332-7");
        byte[] authorizationXml = "<AUTORIZACION/>".getBytes(StandardCharsets.UTF_8);
        CafParserPort.ParsedCaf parsed = new CafParserPort.ParsedCaf(
                "76086428-5",
                33,
                100,
                110,
                LocalDate.of(2026, 1, 1),
                "<CAF/>".getBytes(StandardCharsets.UTF_8),
                true
        );

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(cafParser.parse(authorizationXml)).thenReturn(parsed);

        assertThrows(
                IllegalArgumentException.class,
                () -> cafService.create(tenantId, authorizationXml, "caf.xml")
        );
        verify(storagePort, never()).store(any(), any(), anyLong(), any());
        verify(cafRepository, never()).save(any());
    }

    @Test
    void downloadReturnsOnlyPublicCafAndClearsStoredAuthorizationCopy() throws Exception {
        byte[] authorizationXml = "<AUTORIZACION>private</AUTORIZACION>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] publicCafXml = "<CAF>public</CAF>".getBytes(StandardCharsets.UTF_8);
        Caf caf = new Caf();
        caf.setCafPath("caf/test.xml");
        when(storagePort.get("caf/test.xml")).thenReturn(authorizationXml);
        when(cafParser.parse(authorizationXml)).thenReturn(new CafParserPort.ParsedCaf(
                "10438332-7",
                33,
                100,
                110,
                LocalDate.of(2026, 1, 1),
                publicCafXml,
                true
        ));

        byte[] downloaded = cafService.downloadFile(caf);

        assertArrayEquals(publicCafXml, downloaded);
        assertTrue(allZero(authorizationXml));
    }

    @Test
    void allocateNextFolio_allocatesAndMovesPointer() {
        UUID tenantId = UUID.randomUUID();
        FolioPool pool = buildPool(tenantId, 33, 1, 100L, 110L);

        when(folioAssignmentRepository.findByTenantIdAndRequestId(tenantId, "req-1"))
                .thenReturn(Optional.empty());
        when(folioPoolRepository.lockFirstActivePool(tenantId, 33, 1))
                .thenReturn(Optional.of(pool));
        when(folioPoolRepository.save(any(FolioPool.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folioAssignmentRepository.save(any(FolioAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FolioAssignment assignment = cafService.allocateNextFolio(tenantId, 33, 1, "req-1", "tester");

        assertNotNull(assignment.getId());
        assertEquals(100L, assignment.getFolio());
        assertEquals(CafService.ASSIGNMENT_ASSIGNED, assignment.getStatus());
        assertEquals(101L, pool.getNextFolio());
    }

    @Test
    void allocateNextFolio_returnsExistingForSameRequestId() {
        UUID tenantId = UUID.randomUUID();
        FolioAssignment existing = new FolioAssignment();
        existing.setId(UUID.randomUUID());
        existing.setTipoDte(33);
        existing.setPuntoVenta(1);
        existing.setFolio(777L);

        when(folioAssignmentRepository.findByTenantIdAndRequestId(tenantId, "req-idem"))
                .thenReturn(Optional.of(existing));

        FolioAssignment resolved = cafService.allocateNextFolio(tenantId, 33, 1, "req-idem", "tester");

        assertEquals(existing.getId(), resolved.getId());
        assertEquals(777L, resolved.getFolio());
        verify(folioPoolRepository, never()).lockFirstActivePool(any(), any(), any());
    }

    @Test
    void assignFolioToDte_marksAssignmentUsedAndWritesFolio() {
        UUID tenantId = UUID.randomUUID();
        UUID dteId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        Dte dte = new Dte();
        dte.setId(dteId);
        dte.setTenant(tenant);
        dte.setTipoDte(33);
        dte.setUpdatedAt(Instant.now());

        FolioPool pool = buildPool(tenantId, 33, 1, 200L, 210L);

        when(dteRepository.findByIdAndTenantId(dteId, tenantId)).thenReturn(Optional.of(dte));
        when(folioAssignmentRepository.findByTenantIdAndRequestId(tenantId, "req-dte")).thenReturn(Optional.empty());
        when(folioPoolRepository.lockFirstActivePool(tenantId, 33, 1)).thenReturn(Optional.of(pool));
        when(folioPoolRepository.save(any(FolioPool.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folioAssignmentRepository.save(any(FolioAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dteRepository.save(any(Dte.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FolioAssignment assignment = cafService.assignFolioToDte(tenantId, dteId, 1, "req-dte", "api");

        assertEquals(CafService.ASSIGNMENT_USED, assignment.getStatus());
        assertEquals(200L, dte.getFolio());
        assertNotNull(dte.getFolioAssignment());
        verify(dteRepository).save(eq(dte));
    }

    private FolioPool buildPool(UUID tenantId, int tipoDte, int puntoVenta, long next, long hasta) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        Caf caf = new Caf();
        caf.setId(UUID.randomUUID());
        caf.setTenant(tenant);
        caf.setTipoDte(tipoDte);
        caf.setPuntoVenta(puntoVenta);
        caf.setFolioDesde(next);
        caf.setFolioHasta(hasta);

        FolioPool pool = new FolioPool();
        pool.setId(UUID.randomUUID());
        pool.setTenant(tenant);
        pool.setTipoDte(tipoDte);
        pool.setPuntoVenta(puntoVenta);
        pool.setCaf(caf);
        pool.setFolioDesde(next);
        pool.setFolioHasta(hasta);
        pool.setNextFolio(next);
        pool.setStatus(CafService.POOL_ACTIVE);
        pool.setCreatedAt(Instant.now());
        pool.setUpdatedAt(Instant.now());
        return pool;
    }

    private boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
