package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.application.service.SelfSignedCertGenerator;
import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomXmlSignerAdapterTest {

    private static final String PASSWORD = "test-password";
    private static final String DSIG_NAMESPACE = "http://www.w3.org/2000/09/xmldsig#";

    private UserCertificateRepository repository;
    private CertificateStoragePort storage;
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        repository = mock(UserCertificateRepository.class);
        storage = mock(CertificateStoragePort.class);
        cryptoService = mock(CryptoService.class);
    }

    @Test
    void signsDocumentoWithLegacyProfileAndPreservesDdBytes() throws Exception {
        Fixture fixture = fixture();
        DomXmlSignerAdapter signer = signer();
        byte[] unsigned = unsignedXml();
        byte[] expectedDd = "<DD><IT1>Piñón &amp; engranaje</IT1></DD>"
                .getBytes(StandardCharsets.ISO_8859_1);

        XmlSignerPort.SignedXml signed = signer.sign(request(unsigned, fixture.descriptor()));

        String xml = new String(signed.xml(), StandardCharsets.ISO_8859_1);
        Document document = parse(signed.xml());
        Element documento = (Element) document
                .getElementsByTagNameNS(DomDteXmlBuilderAdapter.SII_NAMESPACE, "Documento")
                .item(0);
        Element signature = nextElement(documento);
        assertEquals("#DTE-105", signed.referenceUri());
        assertEquals(DSIG_NAMESPACE, signature.getNamespaceURI());
        assertEquals("Signature", signature.getLocalName());
        assertEquals(
                DigestMethod.SHA1,
                textAttribute(document, "DigestMethod", "Algorithm")
        );
        assertEquals(
                SignatureMethod.RSA_SHA1,
                textAttribute(document, "SignatureMethod", "Algorithm")
        );
        assertEquals(
                CanonicalizationMethod.INCLUSIVE,
                textAttribute(document, "CanonicalizationMethod", "Algorithm")
        );
        assertEquals(
                "#DTE-105",
                textAttribute(document, "Reference", "URI")
        );
        assertTrue(xml.contains(new String(expectedDd, StandardCharsets.ISO_8859_1)));
        assertEquals(1, document.getElementsByTagNameNS(DSIG_NAMESPACE, "Signature").getLength());

        Element keyInfo = (Element) document
                .getElementsByTagNameNS(DSIG_NAMESPACE, "KeyInfo")
                .item(0);
        assertEquals("KeyValue", firstElement(keyInfo).getLocalName());
        assertEquals("X509Data", nextElement(firstElement(keyInfo)).getLocalName());
    }

    @Test
    void rejectsDuplicateReferenceId() throws Exception {
        Fixture fixture = fixture();
        String xml = new String(unsignedXml(), StandardCharsets.ISO_8859_1)
                .replace("<Caratula/>", "<Caratula ID=\"DTE-105\"/>");

        XmlSignerPort.XmlSigningException exception = assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> signer().sign(request(
                        xml.getBytes(StandardCharsets.ISO_8859_1),
                        fixture.descriptor()
                ))
        );

        assertEquals(
                XmlSignerPort.XmlSigningFailureReason.AMBIGUOUS_TARGET,
                exception.getReason()
        );
    }

    @Test
    void rejectsDoctypeBeforeOpeningCredential() throws Exception {
        Fixture fixture = fixture();
        byte[] malicious = """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <!DOCTYPE EnvioDTE [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <EnvioDTE xmlns="http://www.sii.cl/SiiDte"><SetDTE ID="SetDTE-test">
                <DTE><Documento ID="DTE-105"><TED><DD></DD></TED></Documento></DTE>
                </SetDTE></EnvioDTE>
                """.getBytes(StandardCharsets.ISO_8859_1);

        XmlSignerPort.XmlSigningException exception = assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> signer().sign(request(malicious, fixture.descriptor()))
        );

        assertEquals(
                XmlSignerPort.XmlSigningFailureReason.INVALID_XML,
                exception.getReason()
        );
    }

    @Test
    void rejectsSetDteTargetUntilEnvelopeStep() throws Exception {
        Fixture fixture = fixture();
        XmlSignerPort.SigningRequest request = new XmlSignerPort.SigningRequest(
                unsignedXml(),
                "SetDTE-test",
                XmlSignerPort.SignatureTarget.SET_DTE,
                fixture.descriptor(),
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );

        XmlSignerPort.XmlSigningException exception = assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> signer().sign(request)
        );

        assertEquals(
                XmlSignerPort.XmlSigningFailureReason.UNSUPPORTED_TARGET,
                exception.getReason()
        );
    }

    private DomXmlSignerAdapter signer() {
        return new DomXmlSignerAdapter(
                new Pkcs12SigningCredentialResolver(repository, storage, cryptoService)
        );
    }

    private Fixture fixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = SelfSignedCertGenerator.generate(
                "CN=XMLDSig Test, SERIALNUMBER=10.438.332-7",
                keyPair
        );
        UserCertificateEntity entity = entity(certificate);
        byte[] pkcs12 = pkcs12(keyPair, certificate);

        when(repository.findByIdAndTenantId(entity.getId(), entity.getTenantId()))
                .thenReturn(Optional.of(entity));
        when(storage.get(entity.getCertificatePath()))
                .thenAnswer(invocation -> Arrays.copyOf(pkcs12, pkcs12.length));
        when(cryptoService.decrypt("encrypted-password", "iv")).thenReturn(PASSWORD);
        return new Fixture(descriptor(entity));
    }

    private UserCertificateEntity entity(X509Certificate certificate) {
        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setRutUsuario("10438332-7");
        entity.setCertSubjectRut("10438332-7");
        entity.setCertificatePath("tenants/test/certs/credential.p12");
        entity.setEncryptedPassword("encrypted-password");
        entity.setEncryptionIv("iv");
        entity.setEncryptionAlgorithm("AES/GCM/NoPadding");
        entity.setCertSerialNumber(certificate.getSerialNumber().toString());
        entity.setValidFrom(OffsetDateTime.ofInstant(
                certificate.getNotBefore().toInstant(),
                ZoneOffset.UTC
        ));
        entity.setValidUntil(OffsetDateTime.ofInstant(
                certificate.getNotAfter().toInstant(),
                ZoneOffset.UTC
        ));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private SigningCredentialPort.SigningCredentialDescriptor descriptor(
            UserCertificateEntity entity
    ) {
        return new SigningCredentialPort.SigningCredentialDescriptor(
                entity.getId(),
                entity.getTenantId(),
                entity.getCertSubjectRut(),
                entity.getCertSerialNumber(),
                entity.getValidFrom(),
                entity.getValidUntil()
        );
    }

    private byte[] pkcs12(
            KeyPair keyPair,
            X509Certificate certificate
    ) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = PASSWORD.toCharArray();
        try {
            keyStore.load(null, password);
            keyStore.setKeyEntry(
                    "signing-key",
                    keyPair.getPrivate(),
                    password,
                    new Certificate[]{certificate}
            );
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                keyStore.store(output, password);
                return output.toByteArray();
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private XmlSignerPort.SigningRequest request(
            byte[] xml,
            SigningCredentialPort.SigningCredentialDescriptor descriptor
    ) {
        return new XmlSignerPort.SigningRequest(
                xml,
                "DTE-105",
                XmlSignerPort.SignatureTarget.DOCUMENTO,
                descriptor,
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );
    }

    private byte[] unsignedXml() {
        return """
                <?xml version="1.0" encoding="ISO-8859-1" standalone="no"?>
                <EnvioDTE xmlns="http://www.sii.cl/SiiDte" version="1.0"><SetDTE ID="SetDTE-test"><Caratula/><DTE version="1.0"><Documento ID="DTE-105"><Encabezado/><TED version="1.0"><DD><IT1>Piñón &amp; engranaje</IT1></DD><FRMT algoritmo="SHA1withRSA">signed</FRMT></TED><TmstFirma>2026-07-24T12:30:45</TmstFirma></Documento></DTE></SetDTE></EnvioDTE>
                """.getBytes(StandardCharsets.ISO_8859_1);
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String textAttribute(
            Document document,
            String localName,
            String attribute
    ) {
        return ((Element) document.getElementsByTagNameNS(
                DSIG_NAMESPACE,
                localName
        ).item(0)).getAttribute(attribute);
    }

    private Element firstElement(Element parent) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private Element nextElement(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return sibling;
            }
        }
        return null;
    }

    private record Fixture(
            SigningCredentialPort.SigningCredentialDescriptor descriptor
    ) {
    }
}
