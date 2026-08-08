package cl.cesarg.siiproxyHA.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Produces the lexical DD representation that the SII uses for TED signatures.
 */
final class SiiTedSignatureNormalizer {

    private static final Pattern INTER_TAG_WHITESPACE =
            Pattern.compile(">[\\x20\\x09\\x0D\\x0A]+<");
    private static final Pattern NAMESPACE_DECLARATION = Pattern.compile(
            "\\s+xmlns(?:\\s*:\\s*[A-Za-z_][A-Za-z0-9_.-]*)?"
                    + "\\s*=\\s*(?:\"[^\"]*\"|'[^']*')"
    );

    private SiiTedSignatureNormalizer() {
    }

    static byte[] normalize(byte[] ddXml) {
        Objects.requireNonNull(ddXml, "ddXml is required");
        String source = new String(ddXml, StandardCharsets.ISO_8859_1);
        String withoutNamespaces = removeNamespaceDeclarations(source);
        String normalized = INTER_TAG_WHITESPACE.matcher(withoutNamespaces)
                .replaceAll("><");
        return normalized.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String removeNamespaceDeclarations(String xml) {
        StringBuilder normalized = new StringBuilder(xml.length());
        int fromIndex = 0;
        while (fromIndex < xml.length()) {
            int tagStart = xml.indexOf('<', fromIndex);
            if (tagStart < 0) {
                normalized.append(xml, fromIndex, xml.length());
                break;
            }
            normalized.append(xml, fromIndex, tagStart);
            int tagEnd = tagEnd(xml, tagStart + 1);
            if (tagEnd < 0) {
                normalized.append(xml, tagStart, xml.length());
                break;
            }
            String tag = xml.substring(tagStart, tagEnd + 1);
            normalized.append(NAMESPACE_DECLARATION.matcher(tag).replaceAll(""));
            fromIndex = tagEnd + 1;
        }
        return normalized.toString();
    }

    private static int tagEnd(String xml, int fromIndex) {
        char quote = 0;
        for (int index = fromIndex; index < xml.length(); index++) {
            char current = xml.charAt(index);
            if (quote == 0 && (current == '\'' || current == '"')) {
                quote = current;
            } else if (current == quote) {
                quote = 0;
            } else if (quote == 0 && current == '>') {
                return index;
            }
        }
        return -1;
    }
}
