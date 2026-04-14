package cl.cesarg.siiproxyHA.infrastructure.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CertUtils {
    private static final Pattern RUT_PATTERN = Pattern.compile("([0-9]{1,2}\\.?[0-9]{3}\\.?[0-9]{3}-[0-9Kk])");

    private CertUtils() {}

    public static String extractRutFromSubject(String subject) {
        if (subject == null) return null;
        Matcher m = RUT_PATTERN.matcher(subject);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
