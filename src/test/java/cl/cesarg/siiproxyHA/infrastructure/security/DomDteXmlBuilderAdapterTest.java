package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomDteXmlBuilderAdapterTest {

    private final DomDteXmlBuilderAdapter builder = new DomDteXmlBuilderAdapter();

    @Test
    void buildsNamespaceAwareDomAndPreservesSignedDdBytes() throws Exception {
        byte[] dd = "<DD><IT1>O'Ring &amp; Piñón</IT1></DD>"
                .getBytes(StandardCharsets.ISO_8859_1);
        DteXmlBuilderPort.BuiltDteXml built = builder.build(request(ted(dd)));
        String xml = new String(built.xml(), StandardCharsets.ISO_8859_1);
        Document document = parse(built.xml());

        assertEquals("DTE-105", built.documentoId());
        assertTrue(built.setDteId().startsWith("SetDTE-"));
        assertEquals("ISO-8859-1", built.encoding());
        assertEquals(
                DomDteXmlBuilderAdapter.SII_NAMESPACE,
                document.getDocumentElement().getNamespaceURI()
        );
        assertEquals(
                DomDteXmlBuilderAdapter.ENVIO_DTE_SCHEMA_LOCATION,
                document.getDocumentElement().getAttributeNS(
                        XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,
                        "schemaLocation"
                )
        );
        assertEquals(
                1,
                document.getElementsByTagNameNS(
                        DomDteXmlBuilderAdapter.SII_NAMESPACE,
                        "TED"
                ).getLength()
        );
        assertEquals(
                "Empresa & <DOM>",
                document.getElementsByTagNameNS(
                        DomDteXmlBuilderAdapter.SII_NAMESPACE,
                        "RznSoc"
                ).item(0).getTextContent()
        );
        assertTrue(xml.contains(new String(dd, StandardCharsets.ISO_8859_1)));
        assertTrue(xml.startsWith(DomDteXmlBuilderAdapter.XML_DECLARATION));
        assertFalse(xml.contains("standalone="));
        assertFalse(xml.contains("xmlns=\"\""));
        assertFalse(xml.contains("<Signature"));
    }

    @Test
    void rejectsTedWhoseDeclaredDdDoesNotMatchPayload() {
        byte[] declared = "<DD><F>105</F></DD>"
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] embedded = "<DD><F>106</F></DD>"
                .getBytes(StandardCharsets.ISO_8859_1);
        TedGeneratorPort.GeneratedTed ted = new TedGeneratorPort.GeneratedTed(
                tedXml(embedded),
                declared,
                LocalDateTime.of(2026, 7, 24, 12, 30, 45),
                UUID.randomUUID()
        );

        DteXmlBuilderPort.DteXmlBuildException exception = assertThrows(
                DteXmlBuilderPort.DteXmlBuildException.class,
                () -> builder.build(request(ted))
        );

        assertEquals(
                DteXmlBuilderPort.BuildFailureReason.INVALID_TED,
                exception.getReason()
        );
    }

    @Test
    void rejectsDoctypeInTed() {
        byte[] dd = "<DD></DD>".getBytes(StandardCharsets.ISO_8859_1);
        String xml = """
                <!DOCTYPE TED [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <TED version="1.0"><DD></DD><FRMT algoritmo="SHA1withRSA">signed</FRMT></TED>
                """;
        TedGeneratorPort.GeneratedTed ted = new TedGeneratorPort.GeneratedTed(
                xml.getBytes(StandardCharsets.ISO_8859_1),
                dd,
                LocalDateTime.of(2026, 7, 24, 12, 30, 45),
                UUID.randomUUID()
        );

        DteXmlBuilderPort.DteXmlBuildException exception = assertThrows(
                DteXmlBuilderPort.DteXmlBuildException.class,
                () -> builder.build(request(ted))
        );

        assertEquals(
                DteXmlBuilderPort.BuildFailureReason.INVALID_TED,
                exception.getReason()
        );
    }

    @Test
    void preservesDdProducedFromRealCafMaterialPipeline() throws Exception {
        CafTestFixtureFactory.Fixture fixture = CafTestFixtureFactory.create();
        CafRepository repository = mock(CafRepository.class);
        StoragePort storage = mock(StoragePort.class);
        SecureCafXmlParser parser = new SecureCafXmlParser(1_048_576);
        Caf caf = caf(fixture.xml());
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath())).thenAnswer(invocation -> fixture.xml());
        TedGeneratorAdapter tedGenerator = new TedGeneratorAdapter(
                new CafMaterialAdapter(repository, storage, parser),
                new CafPrivateKeyResolver(repository, storage, parser),
                Clock.fixed(
                        Instant.parse("2026-07-24T16:30:45Z"),
                        ZoneId.of("America/Santiago")
                )
        );
        TedGeneratorPort.GeneratedTed ted = tedGenerator.generate(
                new TedGeneratorPort.TedRequest(
                        caf.getTenant().getId(),
                        CafTestFixtureFactory.RUT_EMISOR,
                        33,
                        1,
                        105,
                        caf.getId(),
                        LocalDate.of(2026, 7, 24),
                        "60803000-K",
                        "SERVICIO DE IMPUESTOS INTERNOS",
                        119_000,
                        "Piñón de prueba"
                )
        );

        DteXmlBuilderPort.BuiltDteXml built = builder.build(request(ted));

        String xml = new String(built.xml(), StandardCharsets.ISO_8859_1);
        assertTrue(xml.contains(new String(ted.ddXml(), StandardCharsets.ISO_8859_1)));
        assertFalse(xml.contains("RSASK"));
        assertFalse(xml.contains("RSAPUBK"));
    }

    private DteXmlBuilderPort.BuildRequest request(
            TedGeneratorPort.GeneratedTed ted
    ) {
        return new DteXmlBuilderPort.BuildRequest(
                UUID.randomUUID(),
                new DteXmlBuilderPort.IssuerData(
                        "76184688-4",
                        "10438332-7",
                        "Empresa & <DOM>",
                        "Servicios",
                        "726000",
                        "Viña del Mar",
                        "Valparaíso",
                        LocalDate.of(2014, 8, 22),
                        80
                ),
                new DteXmlBuilderPort.ReceiverData(
                        "60803000-K",
                        "SERVICIO DE IMPUESTOS INTERNOS",
                        "Gobierno",
                        "Santiago",
                        "Santiago"
                ),
                new DteXmlBuilderPort.DocumentData(
                        33,
                        105,
                        LocalDate.of(2026, 7, 24),
                        100_000L,
                        new BigDecimal("19.00"),
                        19_000L,
                        119_000L
                ),
                List.of(new DteXmlBuilderPort.ItemData(
                        1,
                        "Piñón & engranaje",
                        null,
                        1.0,
                        100_000.0,
                        100_000L
                )),
                List.of(),
                ted
        );
    }

    private TedGeneratorPort.GeneratedTed ted(byte[] dd) {
        return new TedGeneratorPort.GeneratedTed(
                tedXml(dd),
                dd,
                LocalDateTime.of(2026, 7, 24, 12, 30, 45),
                UUID.randomUUID()
        );
    }

    private byte[] tedXml(byte[] dd) {
        return ("<TED version=\"1.0\">"
                + new String(dd, StandardCharsets.ISO_8859_1)
                + "<FRMT algoritmo=\"SHA1withRSA\">signed</FRMT></TED>")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private Caf caf(byte[] authorizationXml) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);

        Caf caf = new Caf();
        caf.setId(UUID.randomUUID());
        caf.setTenant(tenant);
        caf.setTipoDte(33);
        caf.setPuntoVenta(1);
        caf.setFolioDesde(100L);
        caf.setFolioHasta(110L);
        caf.setCafPath("caf/dom-test.xml");
        caf.setCafSha256(sha256(authorizationXml));
        caf.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        caf.setFchAutorizacion(LocalDate.of(2026, 1, 1));
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
}
