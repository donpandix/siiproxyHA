package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CafMaterialAdapterTest {

    private CafRepository repository;
    private StoragePort storage;
    private CafMaterialAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(CafRepository.class);
        storage = mock(StoragePort.class);
        adapter = new CafMaterialAdapter(
                repository,
                storage,
                new SecureCafXmlParser(1_048_576)
        );
    }

    @Test
    void resolvesExactAssignedCafAndReturnsOnlyPublicMaterial() throws Exception {
        CafTestFixtureFactory.Fixture fixture = CafTestFixtureFactory.create();
        UUID tenantId = UUID.randomUUID();
        Caf caf = caf(tenantId, fixture.xml());
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath())).thenReturn(fixture.xml());

        CafMaterialPort.CafMaterial material = adapter.requireCaf(
                new CafMaterialPort.CafMaterialSelector(
                        tenantId,
                        33,
                        1,
                        105,
                        caf.getId()
                )
        );

        assertEquals(caf.getId(), material.descriptor().cafId());
        assertEquals(CafTestFixtureFactory.RUT_EMISOR, material.descriptor().rutEmisor());
        assertEquals(CafTestFixtureFactory.FOLIO_DESDE, material.descriptor().folioDesde());
        assertEquals(CafTestFixtureFactory.FOLIO_HASTA, material.descriptor().folioHasta());
        String publicXml = new String(material.publicCafXml(), StandardCharsets.UTF_8);
        assertFalse(publicXml.contains("RSASK"));
        assertFalse(publicXml.contains("RSAPUBK"));
    }

    @Test
    void rejectsChecksumMismatch() throws Exception {
        CafTestFixtureFactory.Fixture fixture = CafTestFixtureFactory.create();
        UUID tenantId = UUID.randomUUID();
        Caf caf = caf(tenantId, fixture.xml());
        caf.setCafSha256("0".repeat(64));
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath())).thenReturn(fixture.xml());

        CafMaterialPort.CafMaterialUnavailableException exception = assertThrows(
                CafMaterialPort.CafMaterialUnavailableException.class,
                () -> adapter.requireCaf(new CafMaterialPort.CafMaterialSelector(
                        tenantId,
                        33,
                        1,
                        105,
                        caf.getId()
                ))
        );

        assertEquals(
                CafMaterialPort.CafFailureReason.INTEGRITY_FAILURE,
                exception.getReason()
        );
    }

    @Test
    void rejectsAmbiguousRangeWhenAssignmentDoesNotIdentifyCaf() {
        UUID tenantId = UUID.randomUUID();
        Caf first = caf(tenantId, new byte[]{1});
        Caf second = caf(tenantId, new byte[]{2});
        when(repository.findByTenantIdAndTipoDteAndPuntoVentaAndActiveTrueOrderByCreatedAtAsc(
                tenantId,
                33,
                1
        )).thenReturn(List.of(first, second));

        CafMaterialPort.CafMaterialUnavailableException exception = assertThrows(
                CafMaterialPort.CafMaterialUnavailableException.class,
                () -> adapter.requireCaf(
                        new CafMaterialPort.CafMaterialSelector(tenantId, 33, 1, 105)
                )
        );

        assertEquals(CafMaterialPort.CafFailureReason.AMBIGUOUS, exception.getReason());
    }

    private Caf caf(UUID tenantId, byte[] authorizationXml) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);

        Caf caf = new Caf();
        caf.setId(UUID.randomUUID());
        caf.setTenant(tenant);
        caf.setTipoDte(CafTestFixtureFactory.TIPO_DTE);
        caf.setPuntoVenta(1);
        caf.setFolioDesde(CafTestFixtureFactory.FOLIO_DESDE);
        caf.setFolioHasta(CafTestFixtureFactory.FOLIO_HASTA);
        caf.setCafPath("caf/test.xml");
        caf.setCafSha256(sha256(authorizationXml));
        caf.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        caf.setFchAutorizacion(LocalDate.of(2026, 1, 1));
        caf.setCreatedAt(Instant.now());
        caf.setActive(true);
        return caf;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
