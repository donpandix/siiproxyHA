package cl.cesarg.siiproxyHA.infrastructure.security;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public final class CertificateParser {

    private CertificateParser() {}

    public static X509Certificate parse(byte[] data, String filename, String password) throws Exception {
        String name = filename == null ? "" : filename.toLowerCase();

        // Try PKCS12
        if (name.endsWith(".p12") || name.endsWith(".pfx")) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new ByteArrayInputStream(data)) {
                ks.load(in, password == null ? new char[0] : password.toCharArray());
            }
            var aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    return (X509Certificate) cert;
                }
            }
            throw new IllegalArgumentException("No X.509 certificate found in PKCS12");
        }

        // Try PEM / DER
        try (InputStream in = new ByteArrayInputStream(data)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            var certs = cf.generateCertificates(in);
            if (certs != null && !certs.isEmpty()) {
                Certificate c = certs.iterator().next();
                if (c instanceof X509Certificate) {
                    return (X509Certificate) c;
                }
            }
        }

        throw new IllegalArgumentException("Unable to parse X.509 certificate from input");
    }
}
