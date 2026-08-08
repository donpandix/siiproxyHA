package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TedGeneratorAdapterTest {

    private CafRepository repository;
    private StoragePort storage;
    private TedGeneratorAdapter generator;
    private Caf caf;
    private CafTestFixtureFactory.Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = CafTestFixtureFactory.create();
        repository = mock(CafRepository.class);
        storage = mock(StoragePort.class);
        SecureCafXmlParser parser = new SecureCafXmlParser(1_048_576);
        CafMaterialAdapter materialAdapter = new CafMaterialAdapter(
                repository,
                storage,
                parser
        );
        CafPrivateKeyResolver privateKeyResolver = new CafPrivateKeyResolver(
                repository,
                storage,
                parser
        );
        generator = new TedGeneratorAdapter(
                materialAdapter,
                privateKeyResolver,
                Clock.fixed(
                        Instant.parse("2026-07-24T16:30:45Z"),
                        ZoneId.of("America/Santiago")
                )
        );
        caf = caf(fixture.xml());
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath())).thenAnswer(invocation -> fixture.xml());
    }

    @Test
    void buildsPublicTedAndSignsSiiNormalizedDdBytes() throws Exception {
        TedGeneratorPort.GeneratedTed generated = generator.generate(request(
                "SERVICIO DE IMPUESTOS INTERNOS CON TEXTO EXCEDENTE",
                "Artículo de prueba"
        ));

        String tedXml = new String(
                generated.tedXml(),
                StandardCharsets.ISO_8859_1
        );
        String ddXml = new String(
                generated.ddXml(),
                StandardCharsets.ISO_8859_1
        );

        assertEquals(LocalDateTime.of(2026, 7, 24, 12, 30, 45), generated.generatedAt());
        assertEquals(caf.getId(), generated.cafId());
        assertTrue(ddXml.contains("<RSR>SERVICIO DE IMPUESTOS INTERNOS CON TEXTO</RSR>"));
        assertTrue(ddXml.contains("<IT1>Artículo de prueba</IT1>"));
        assertTrue(ddXml.contains("<CAF version=\"1.0\">"));
        assertTrue(ddXml.contains("<TSTED>2026-07-24T12:30:45</TSTED>"));
        assertTrue(ddXml.startsWith("<DD>\n<RE>"));
        assertTrue(ddXml.contains("</IT1>\n<CAF"));
        assertTrue(ddXml.contains("</CAF>\n<TSTED>"));
        assertTrue(ddXml.endsWith("</TSTED>\n</DD>"));
        assertFalse(tedXml.contains("\r"));
        assertFalse(tedXml.contains("&#13;"));
        assertFalse(tedXml.contains("RSASK"));
        assertFalse(tedXml.contains("RSAPUBK"));
        byte[] signingDd = SiiTedSignatureNormalizer.normalize(generated.ddXml());
        byte[] alteredSigningDd = new String(signingDd, StandardCharsets.ISO_8859_1)
                .replace("Artículo de prueba", "Artículo alterado")
                .getBytes(StandardCharsets.ISO_8859_1);
        try {
            assertFalse(verifyFrmt(tedXml, generated.ddXml()));
            assertTrue(verifyFrmt(tedXml, signingDd));
            assertFalse(verifyFrmt(tedXml, alteredSigningDd));
        } finally {
            java.util.Arrays.fill(signingDd, (byte) 0);
            java.util.Arrays.fill(alteredSigningDd, (byte) 0);
        }
    }

    @Test
    void rejectsCharactersOutsideLatin1() {
        TedGeneratorAdapter.TedGenerationException exception = assertThrows(
                TedGeneratorAdapter.TedGenerationException.class,
                () -> generator.generate(request("RECEPTOR", "Producto 🚀"))
        );

        assertEquals(
                TedGeneratorAdapter.TedFailureReason.UNSUPPORTED_CHARACTER,
                exception.getReason()
        );
    }

    @Test
    void rejectsEmitterThatDoesNotMatchSelectedCaf() {
        TedGeneratorPort.TedRequest mismatched = new TedGeneratorPort.TedRequest(
                caf.getTenant().getId(),
                "76184688-4",
                CafTestFixtureFactory.TIPO_DTE,
                1,
                105,
                caf.getId(),
                LocalDate.of(2026, 7, 24),
                "60803000-K",
                "RECEPTOR",
                120_000,
                "Producto"
        );

        TedGeneratorAdapter.TedGenerationException exception = assertThrows(
                TedGeneratorAdapter.TedGenerationException.class,
                () -> generator.generate(mismatched)
        );

        assertEquals(
                TedGeneratorAdapter.TedFailureReason.CAF_MISMATCH,
                exception.getReason()
        );
    }

    private TedGeneratorPort.TedRequest request(
            String receiverName,
            String firstItem
    ) {
        return new TedGeneratorPort.TedRequest(
                caf.getTenant().getId(),
                CafTestFixtureFactory.RUT_EMISOR,
                CafTestFixtureFactory.TIPO_DTE,
                1,
                105,
                caf.getId(),
                LocalDate.of(2026, 7, 24),
                "60803000-K",
                receiverName,
                120_000,
                firstItem
        );
    }

    private boolean verifyFrmt(String tedXml, byte[] ddXml) throws Exception {
        String opening = "<FRMT algoritmo=\"SHA1withRSA\">";
        int start = tedXml.indexOf(opening) + opening.length();
        int end = tedXml.indexOf("</FRMT>", start);
        byte[] signatureBytes = Base64.getDecoder().decode(
                tedXml.substring(start, end)
        );
        try {
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(fixture.keyPair().getPublic());
            verifier.update(ddXml);
            return verifier.verify(signatureBytes);
        } finally {
            java.util.Arrays.fill(signatureBytes, (byte) 0);
        }
    }

    private Caf caf(byte[] authorizationXml) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);

        Caf entity = new Caf();
        entity.setId(UUID.randomUUID());
        entity.setTenant(tenant);
        entity.setTipoDte(CafTestFixtureFactory.TIPO_DTE);
        entity.setPuntoVenta(1);
        entity.setFolioDesde(CafTestFixtureFactory.FOLIO_DESDE);
        entity.setFolioHasta(CafTestFixtureFactory.FOLIO_HASTA);
        entity.setCafPath("caf/ted-test.xml");
        entity.setCafSha256(sha256(authorizationXml));
        entity.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        entity.setFchAutorizacion(LocalDate.of(2026, 1, 1));
        entity.setCreatedAt(Instant.now());
        entity.setActive(true);
        return entity;
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
}
