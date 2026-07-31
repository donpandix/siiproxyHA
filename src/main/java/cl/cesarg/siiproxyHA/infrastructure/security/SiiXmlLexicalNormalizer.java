package cl.cesarg.siiproxyHA.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Keeps EnvioDTE serialization lexically aligned with the accepted SII sample.
 */
final class SiiXmlLexicalNormalizer {

    private static final String GENERATED_ROOT =
            "<EnvioDTE xmlns=\"http://www.sii.cl/SiiDte\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " version=\"1.0\""
                    + " xsi:schemaLocation=\"http://www.sii.cl/SiiDte EnvioDTE_v10.xsd\">";
    static final String ACCEPTED_ROOT =
            "<EnvioDTE xmlns=\"http://www.sii.cl/SiiDte\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " xsi:schemaLocation=\"http://www.sii.cl/SiiDte EnvioDTE_v10.xsd\""
                    + " version=\"1.0\">";
    private static final String SIGNATURE_OPEN =
            "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">";
    private static final String GENERATED_SIGNED_INFO = "<SignedInfo>";
    private static final String DOCUMENT_SIGNED_INFO =
            "<SignedInfo xmlns=\"http://www.w3.org/2000/09/xmldsig#\">";
    private static final String ENVELOPE_SIGNED_INFO =
            "<SignedInfo xmlns=\"http://www.w3.org/2000/09/xmldsig#\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">";

    private SiiXmlLexicalNormalizer() {
    }

    static byte[] alignEnvioDteRoot(byte[] xml) {
        String value = new String(xml, StandardCharsets.ISO_8859_1);
        if (value.contains(ACCEPTED_ROOT)) {
            return Arrays.copyOf(xml, xml.length);
        }
        int rootStart = value.indexOf(GENERATED_ROOT);
        if (rootStart < 0) {
            throw new IllegalArgumentException(
                    "EnvioDTE root attributes do not match the supported profile"
            );
        }
        String aligned = value.substring(0, rootStart)
                + ACCEPTED_ROOT
                + value.substring(rootStart + GENERATED_ROOT.length());
        return aligned.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Restores redundant namespace declarations intentionally present in the accepted SII XML.
     * The JAXP Transformer removes them even when they are explicit DOM attributes.
     */
    static byte[] alignSignedInfoNamespaces(
            byte[] xml,
            List<Boolean> includeXsiBySignature
    ) {
        String value = new String(xml, StandardCharsets.ISO_8859_1);
        int searchFrom = 0;
        for (boolean includeXsi : includeXsiBySignature) {
            int signatureStart = value.indexOf(SIGNATURE_OPEN, searchFrom);
            if (signatureStart < 0) {
                throw new IllegalArgumentException(
                        "XMLDSig Signature count does not match the DOM profile"
                );
            }
            int signedInfoStart = signatureStart + SIGNATURE_OPEN.length();
            String accepted = includeXsi
                    ? ENVELOPE_SIGNED_INFO
                    : DOCUMENT_SIGNED_INFO;
            if (value.startsWith(GENERATED_SIGNED_INFO, signedInfoStart)) {
                value = value.substring(0, signedInfoStart)
                        + accepted
                        + value.substring(
                                signedInfoStart + GENERATED_SIGNED_INFO.length()
                        );
            } else if (!value.startsWith(accepted, signedInfoStart)) {
                throw new IllegalArgumentException(
                        "SignedInfo namespace declarations do not match the supported profile"
                );
            }
            searchFrom = signedInfoStart + accepted.length();
        }
        if (value.indexOf(SIGNATURE_OPEN, searchFrom) >= 0) {
            throw new IllegalArgumentException(
                    "XMLDSig Signature count does not match the DOM profile"
            );
        }
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
