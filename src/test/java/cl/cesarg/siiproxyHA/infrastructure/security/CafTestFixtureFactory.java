package cl.cesarg.siiproxyHA.infrastructure.security;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

public final class CafTestFixtureFactory {

    public static final String RUT_EMISOR = "10438332-7";
    public static final int TIPO_DTE = 33;
    public static final long FOLIO_DESDE = 100;
    public static final long FOLIO_HASTA = 110;

    private CafTestFixtureFactory() {
    }

    public static Fixture create() throws Exception {
        return create(true, false);
    }

    public static Fixture createWithoutPrivateKey() throws Exception {
        return create(false, false);
    }

    public static Fixture createWithMismatchedPrivateKey() throws Exception {
        return create(true, true);
    }

    private static Fixture create(
            boolean includePrivateKey,
            boolean mismatchedPrivateKey
    ) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();
        KeyPair publicKeyPair = mismatchedPrivateKey
                ? generator.generateKeyPair()
                : keyPair;
        RSAPublicKey publicKey = (RSAPublicKey) publicKeyPair.getPublic();

        String modulus = Base64.getEncoder().encodeToString(unsigned(publicKey.getModulus()));
        String exponent = Base64.getEncoder().encodeToString(
                unsigned(publicKey.getPublicExponent())
        );
        String frma = Base64.getEncoder().encodeToString(
                "synthetic-sii-signature".getBytes(StandardCharsets.UTF_8)
        );
        String privateKeyElement = "";
        if (includePrivateKey) {
            StringWriter pem = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(pem)) {
                writer.writeObject(keyPair.getPrivate());
            }
            privateKeyElement = "<RSA" + "SK>" + pem + "</RSA" + "SK>";
        }

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <AUTORIZACION>
                  <CAF version="1.0">
                    <DA>
                      <RE>%s</RE>
                      <RS>TEST EMISOR</RS>
                      <TD>%d</TD>
                      <RNG><D>%d</D><H>%d</H></RNG>
                      <FA>2026-01-01</FA>
                      <RSAPK><M>%s</M><E>%s</E></RSAPK>
                      <IDK>100</IDK>
                    </DA>
                    <FRMA algoritmo="SHA1withRSA">%s</FRMA>
                  </CAF>
                  %s
                  <RSAPUBK>EXTERNAL-PUBLIC-MATERIAL</RSAPUBK>
                </AUTORIZACION>
                """.formatted(
                RUT_EMISOR,
                TIPO_DTE,
                FOLIO_DESDE,
                FOLIO_HASTA,
                modulus,
                exponent,
                frma,
                privateKeyElement
        );

        return new Fixture(xml.getBytes(StandardCharsets.UTF_8), keyPair);
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] encoded = value.toByteArray();
        if (encoded.length > 1 && encoded[0] == 0) {
            return Arrays.copyOfRange(encoded, 1, encoded.length);
        }
        return encoded;
    }

    public record Fixture(byte[] xml, KeyPair keyPair) {

        public Fixture {
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }
}
