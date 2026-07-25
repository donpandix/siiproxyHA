package cl.cesarg.siiproxyHA.infrastructure.security;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

import javax.security.auth.x500.X500Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CertUtils {
    private static final Pattern RUT_PATTERN = Pattern.compile("([0-9]{1,2}\\.?[0-9]{3}\\.?[0-9]{3}-[0-9Kk])");

    private CertUtils() {}

    public static String extractRutFromPrincipal(X500Principal principal) {
        if (principal == null) return null;

        X500Name subject = X500Name.getInstance(principal.getEncoded());
        RDN[] serialNumbers = subject.getRDNs(BCStyle.SERIALNUMBER);
        for (RDN serialNumber : serialNumbers) {
            if (serialNumber.getFirst() == null) continue;
            String value = IETFUtils.valueToString(serialNumber.getFirst().getValue());
            String rut = extractRutFromSubject(value);
            if (rut != null) return rut;
        }

        // Fallback for certificates that store the RUT in another Subject attribute.
        return extractRutFromSubject(principal.getName(X500Principal.RFC1779));
    }

    public static String extractRutFromSubject(String subject) {
        if (subject == null) return null;
        Matcher m = RUT_PATTERN.matcher(subject);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
