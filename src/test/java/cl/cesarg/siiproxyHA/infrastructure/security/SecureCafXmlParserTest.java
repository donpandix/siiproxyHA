package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort;
import cl.cesarg.siiproxyHA.domain.port.CafParserPort;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureCafXmlParserTest {

    private final SecureCafXmlParser parser = new SecureCafXmlParser(1_048_576);

    @Test
    void extractsOnlyPublicCafAndVerifiesPrivateKey() throws Exception {
        CafTestFixtureFactory.Fixture fixture = CafTestFixtureFactory.create();

        CafParserPort.ParsedCaf parsed = parser.parse(fixture.xml());
        String publicXml = new String(parsed.publicCafXml(), StandardCharsets.UTF_8);
        SecureCafXmlParser.ParsedAuthorization privateMaterial =
                parser.parsePrivateMaterial(fixture.xml());

        assertEquals(CafTestFixtureFactory.RUT_EMISOR, parsed.rutEmisor());
        assertEquals(CafTestFixtureFactory.TIPO_DTE, parsed.tipoDte());
        assertEquals(CafTestFixtureFactory.FOLIO_DESDE, parsed.folioDesde());
        assertEquals(CafTestFixtureFactory.FOLIO_HASTA, parsed.folioHasta());
        assertTrue(parsed.privateKeyAvailable());
        assertTrue(publicXml.contains("<CAF"));
        assertFalse(publicXml.contains("RSASK"));
        assertFalse(publicXml.contains("RSAPUBK"));
        assertNotNull(privateMaterial.privateKey());
        assertEquals("RSA", privateMaterial.privateKey().getAlgorithm());
    }

    @Test
    void acceptsSanitizedPublicOnlyCaf() throws Exception {
        CafTestFixtureFactory.Fixture fixture =
                CafTestFixtureFactory.createWithoutPrivateKey();

        CafParserPort.ParsedCaf parsed = parser.parse(fixture.xml());

        assertFalse(parsed.privateKeyAvailable());
    }

    @Test
    void rejectsDoctypeAndExternalEntityDeclarations() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE AUTORIZACION [
                  <!ENTITY external SYSTEM "file:///etc/passwd">
                ]>
                <AUTORIZACION><CAF>&external;</CAF></AUTORIZACION>
                """;

        CafMaterialPort.CafMaterialUnavailableException exception = assertThrows(
                CafMaterialPort.CafMaterialUnavailableException.class,
                () -> parser.parse(xml.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(
                CafMaterialPort.CafFailureReason.INVALID_XML,
                exception.getReason()
        );
    }

    @Test
    void rejectsOversizedCafBeforeParsing() {
        SecureCafXmlParser smallParser = new SecureCafXmlParser(10);

        CafMaterialPort.CafMaterialUnavailableException exception = assertThrows(
                CafMaterialPort.CafMaterialUnavailableException.class,
                () -> smallParser.parse("<CAF>too-large</CAF>".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(
                CafMaterialPort.CafFailureReason.INVALID_XML,
                exception.getReason()
        );
    }

    @Test
    void rejectsPrivateKeyThatDoesNotMatchPublicCafKey() throws Exception {
        CafTestFixtureFactory.Fixture fixture =
                CafTestFixtureFactory.createWithMismatchedPrivateKey();

        CafMaterialPort.CafMaterialUnavailableException exception = assertThrows(
                CafMaterialPort.CafMaterialUnavailableException.class,
                () -> parser.parse(fixture.xml())
        );

        assertEquals(
                CafMaterialPort.CafFailureReason.INTEGRITY_FAILURE,
                exception.getReason()
        );
    }
}
