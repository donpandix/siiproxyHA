package cl.cesarg.siiproxyHA.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiiTedSignatureNormalizerTest {

    @Test
    void removesOnlyWhitespaceBetweenTags() {
        String dd = """
                <DD>
                  <RSR>Empresa  con espacios</RSR>\r
                \t<IT1>Piñón &amp; engranaje</IT1>
                </DD>
                """.stripTrailing();

        assertEquals(
                "<DD><RSR>Empresa  con espacios</RSR>"
                        + "<IT1>Piñón &amp; engranaje</IT1></DD>",
                normalize(dd)
        );
    }

    @Test
    void removesDefaultAndPrefixedNamespaceDeclarationsFromTags() {
        String dd = "<DD xmlns=\"http://www.sii.cl/SiiDte\""
                + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"
                + "<RE>76184688-4</RE></DD>";

        assertEquals("<DD><RE>76184688-4</RE></DD>", normalize(dd));
    }

    @Test
    void preservesWhitespaceInsideTerminalElementValues() {
        String dd = "<DD>\n<RSR> Empresa \t Limitada </RSR>\n</DD>";

        assertEquals(
                "<DD><RSR> Empresa \t Limitada </RSR></DD>",
                normalize(dd)
        );
    }

    @Test
    void producesSamePayloadForLfCrLfTabsAndIndentedLayout() {
        String compact = "<DD><RE>76184688-4</RE><F>189</F></DD>";
        String formatted = "<DD>\r\n\t<RE>76184688-4</RE> \n  <F>189</F>\r\n</DD>";

        assertEquals(normalize(compact), normalize(formatted));
    }

    private String normalize(String value) {
        return new String(
                SiiTedSignatureNormalizer.normalize(
                        value.getBytes(StandardCharsets.ISO_8859_1)
                ),
                StandardCharsets.ISO_8859_1
        );
    }
}
