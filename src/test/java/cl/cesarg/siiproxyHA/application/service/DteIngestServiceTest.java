package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteIngestPayload;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DteIngestServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock UserCertificateService userCertificateService;
    @Mock ReceptorService receptorService;
    @Mock DteCrudService dteCrudService;
    @Mock CafService cafService;
    @Mock DteService dteService;

    private DteIngestService service;

    @BeforeEach
    void setUp() {
        service = new DteIngestService(
                tenantRepository,
                userCertificateService,
                receptorService,
                dteCrudService,
                cafService,
                dteService
        );
    }

    @Test
    void validatesRutEnviaUpsertsReceptorAndDoesNotModifyTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setTenantCode("TENANT-1");
        tenant.setRutEmisor("76184688-4");
        tenant.setFchResol(LocalDate.of(2014, 8, 22));
        tenant.setNroResol(80);

        Receptor receptor = new Receptor();
        receptor.setId(UUID.randomUUID());
        receptor.setTenant(tenant);
        receptor.setRutReceptor("60803000-K");
        receptor.setRazonSocial("SERVICIO DE IMPUESTOS INTERNOS");
        receptor.setGiro("SERVICIO PUBLICO");
        receptor.setEmail("contacto@sii.cl");
        receptor.setTelefono("223951000");
        receptor.setDireccion("TEATINOS 120");
        receptor.setComuna("SANTIAGO");
        receptor.setCiudad("SANTIAGO");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userCertificateService.requireActiveCertificate(tenantId, "10438332-7"))
                .thenReturn(new UserCertificateEntity());
        when(receptorService.upsert(any(), any())).thenReturn(receptor);
        when(dteCrudService.create(any(Dte.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentMetadata stored = new DocumentMetadata(UUID.randomUUID().toString(), DocumentStatus.STORED);
        when(dteService.store(any(Dte.class))).thenReturn(stored);

        DocumentMetadata result = service.ingest(validPayload(tenantId));

        ArgumentCaptor<Dte> dteCaptor = ArgumentCaptor.forClass(Dte.class);
        verify(dteCrudService).create(dteCaptor.capture());
        Dte dte = dteCaptor.getValue();
        assertSame(tenant, dte.getTenant());
        assertEquals("76184688-4", tenant.getRutEmisor());
        assertEquals("10438332-7", dte.getRutEnvia());
        assertSame(receptor, dte.getReceptor());
        assertEquals("60803000-K", dte.getRutRecep());
        assertEquals("SERVICIO PUBLICO", dte.getGiroRecep());
        assertEquals("223951000", dte.getTelefonoRecep());
        assertEquals(2, dte.getItems().size());
        assertNotNull(dte.getItems().get(0).getId());
        assertNotNull(dte.getItems().get(1).getId());
        assertNotEquals(dte.getItems().get(0).getId(), dte.getItems().get(1).getId());
        assertSame(stored, result);
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void returnsExistingDocumentForAnIdempotentReplay() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID dteId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setTenantCode("TENANT-1");
        tenant.setRutEmisor("76184688-4");
        tenant.setFchResol(LocalDate.of(2014, 8, 22));
        tenant.setNroResol(80);
        Dte existing = new Dte();
        existing.setId(dteId);
        existing.setTenant(tenant);
        DocumentMetadata stored = new DocumentMetadata(dteId.toString(), DocumentStatus.STORED);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userCertificateService.requireActiveCertificate(tenantId, "10438332-7"))
                .thenReturn(new UserCertificateEntity());
        when(dteCrudService.findForStorage(dteId, tenantId)).thenReturn(Optional.of(existing));
        when(dteService.store(existing)).thenReturn(stored);
        DteIngestPayload payload = validPayload(tenantId);
        payload.id = dteId.toString();

        DocumentMetadata result = service.ingest(payload);

        assertSame(stored, result);
        verify(receptorService, never()).upsert(any(), any());
        verify(dteCrudService, never()).create(any());
        verify(cafService, never()).assignFolioToDte(any(), any(), any(), any(), any());
    }

    private DteIngestPayload validPayload(UUID tenantId) {
        DteIngestPayload payload = new DteIngestPayload();
        payload.tenantId = tenantId.toString();
        payload.tenantCode = "TENANT-1";
        payload.rutEnvia = "10.438.332-7";
        payload.tipoDte = 33;
        payload.folio = 182L;
        payload.fchEmis = "2026-02-15";
        payload.mntNeto = 7000L;
        payload.iva = 1330L;
        payload.mntTotal = 8330L;

        DteIngestPayload.Item firstItem = new DteIngestPayload.Item();
        firstItem.nroLinDet = 1;
        firstItem.nmbItem = "Producto A";
        firstItem.qtyItem = 2.0;
        firstItem.prcItem = 1000.0;
        firstItem.montoItem = 2000L;

        DteIngestPayload.Item secondItem = new DteIngestPayload.Item();
        secondItem.nroLinDet = 2;
        secondItem.nmbItem = "Producto B";
        secondItem.qtyItem = 5.0;
        secondItem.prcItem = 1000.0;
        secondItem.montoItem = 5000L;
        payload.items = java.util.List.of(firstItem, secondItem);

        payload.receptor = new DteIngestPayload.Receptor();
        payload.receptor.rutReceptor = "60803000-K";
        payload.receptor.razonSocial = "SERVICIO DE IMPUESTOS INTERNOS";
        payload.receptor.giro = "SERVICIO PUBLICO";
        payload.receptor.email = "contacto@sii.cl";
        payload.receptor.telefono = "223951000";
        payload.receptor.direccion = "TEATINOS 120";
        payload.receptor.comuna = "SANTIAGO";
        payload.receptor.ciudad = "SANTIAGO";
        return payload;
    }
}
