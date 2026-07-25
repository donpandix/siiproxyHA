package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort;
import cl.cesarg.siiproxyHA.domain.port.CafParserPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CafPrivateKeyResolverTest {

    private CafRepository repository;
    private StoragePort storage;
    private SecureCafXmlParser parser;
    private CafPrivateKeyResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(CafRepository.class);
        storage = mock(StoragePort.class);
        parser = new SecureCafXmlParser(1_048_576);
        resolver = new CafPrivateKeyResolver(repository, storage, parser);
    }

    @Test
    void scopesPrivateKeyToCallbackAndClearsDownloadedXml() throws Exception {
        CafTestFixtureFactory.Fixture fixture = CafTestFixtureFactory.create();
        byte[] downloaded = fixture.xml();
        Caf caf = caf(fixture.xml());
        CafMaterialPort.CafMaterial material = material(caf, fixture.xml());
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath())).thenReturn(downloaded);

        String algorithm = resolver.withPrivateKey(
                material,
                privateKey -> privateKey.getAlgorithm()
        );

        assertEquals("RSA", algorithm);
        assertTrue(allZero(downloaded));
    }

    @Test
    void rejectsSanitizedCafWithoutPrivateKey() throws Exception {
        CafTestFixtureFactory.Fixture fixture =
                CafTestFixtureFactory.createWithoutPrivateKey();
        Caf caf = caf(fixture.xml());
        CafMaterialPort.CafMaterial material = material(caf, fixture.xml());
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath())).thenReturn(fixture.xml());

        CafMaterialPort.CafMaterialUnavailableException exception = assertThrows(
                CafMaterialPort.CafMaterialUnavailableException.class,
                () -> resolver.withPrivateKey(material, privateKey -> null)
        );

        assertEquals(
                CafMaterialPort.CafFailureReason.PRIVATE_KEY_UNAVAILABLE,
                exception.getReason()
        );
    }

    private CafMaterialPort.CafMaterial material(Caf caf, byte[] authorizationXml) {
        CafParserPort.ParsedCaf parsed = parser.parse(authorizationXml);
        CafMaterialPort.CafMaterialDescriptor descriptor =
                new CafMaterialPort.CafMaterialDescriptor(
                        caf.getId(),
                        caf.getTenant().getId(),
                        parsed.rutEmisor(),
                        parsed.tipoDte(),
                        caf.getPuntoVenta(),
                        parsed.folioDesde(),
                        parsed.folioHasta(),
                        parsed.authorizationDate()
                );
        return new CafMaterialPort.CafMaterial(descriptor, parsed.publicCafXml());
    }

    private Caf caf(byte[] authorizationXml) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);

        Caf caf = new Caf();
        caf.setId(UUID.randomUUID());
        caf.setTenant(tenant);
        caf.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        caf.setTipoDte(CafTestFixtureFactory.TIPO_DTE);
        caf.setPuntoVenta(1);
        caf.setFolioDesde(CafTestFixtureFactory.FOLIO_DESDE);
        caf.setFolioHasta(CafTestFixtureFactory.FOLIO_HASTA);
        caf.setFchAutorizacion(LocalDate.of(2026, 1, 1));
        caf.setCafPath("caf/test.xml");
        caf.setCafSha256(sha256(authorizationXml));
        caf.setCreatedAt(Instant.now());
        caf.setActive(true);
        return caf;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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
