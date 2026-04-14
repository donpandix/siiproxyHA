package cl.cesarg.siiproxyHA.application.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SelfSignedCertGenerator {
    public static X509Certificate generate(String subjectDn, KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 1000L * 60);
        Date notAfter = new Date(now + 1000L * 60 * 60 * 24 * 365);

        X500Name dn = new X500Name(subjectDn);
        BigInteger serial = BigInteger.valueOf(new SecureRandom().nextLong()).abs();

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dn,
                serial,
                notBefore,
                notAfter,
                dn,
                keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
        cert.checkValidity(new Date());
        cert.verify(keyPair.getPublic());
        return cert;
    }
}
