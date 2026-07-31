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

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(
                Transform.ENVELOPED,
                textAttribute(document, "Transform", "Algorithm")
        );
        assertTrue(xml.contains(new String(expectedDd, StandardCharsets.ISO_8859_1)));
        assertTrue(xml.contains("</Documento>\n<Signature"));
        assertTrue(xml.contains("</Signature>\n</DTE>"));
        assertFalse(xml.contains("\r"));
        assertFalse(xml.contains("&#13;"));
        assertEquals(1, document.getElementsByTagNameNS(DSIG_NAMESPACE, "Signature").getLength());
        assertDocumentoSignedInfoNamespaces(document);
        assertBase64LineWidth64(document, "SignatureValue");
        assertBase64LineWidth64(document, "Modulus");
        assertBase64LineWidth64(document, "Exponent");
        assertBase64LineWidth64(document, "X509Certificate");

        Element keyInfo = (Element) document
                .getElementsByTagNameNS(DSIG_NAMESPACE, "KeyInfo")
                .item(0);
        assertEquals("KeyValue", firstElement(keyInfo).getLocalName());
        assertEquals("X509Data", nextElement(firstElement(keyInfo)).getLocalName());
    }

    @Test
    void signsDocumentoAndSetDteOnOneDomWithAcceptedReferenceTransform()
            throws Exception {
        Fixture fixture = fixture();

        XmlSignerPort.SignedXml signed = signer().signChain(
                new XmlSignerPort.ChainedSigningRequest(
                        unsignedXml(),
                        "DTE-105",
                        "SetDTE-test",
                        fixture.descriptor(),
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );

        String xml = new String(signed.xml(), StandardCharsets.ISO_8859_1);
        Document document = parse(signed.xml());
        assertTrue(xml.startsWith(
                DomDteXmlBuilderAdapter.XML_DECLARATION
                        + SiiXmlLexicalNormalizer.ACCEPTED_ROOT
        ));
        assertEquals(XmlSignerPort.SignatureTarget.SET_DTE, signed.target());
        assertEquals("#SetDTE-test", signed.referenceUri());
        assertEquals(
                2,
                document.getElementsByTagNameNS(DSIG_NAMESPACE, "Signature").getLength()
        );
        assertEquals(
                "#DTE-105",
                ((Element) document.getElementsByTagNameNS(
                        DSIG_NAMESPACE,
                        "Reference"
                ).item(0)).getAttribute("URI")
        );
        assertEquals(
                "#SetDTE-test",
                ((Element) document.getElementsByTagNameNS(
                        DSIG_NAMESPACE,
                        "Reference"
                ).item(1)).getAttribute("URI")
        );
        var transforms = document.getElementsByTagNameNS(DSIG_NAMESPACE, "Transform");
        assertEquals(2, transforms.getLength());
        assertEquals(
                Transform.ENVELOPED,
                ((Element) transforms.item(0)).getAttribute("Algorithm")
        );
        assertEquals(
                Transform.ENVELOPED,
                ((Element) transforms.item(1)).getAttribute("Algorithm")
        );
        var signatures = document.getElementsByTagNameNS(
                DSIG_NAMESPACE,
                "Signature"
        );
        assertEquals(
                java.util.List.of("SignedInfo", "SignatureValue", "KeyInfo"),
                directChildNames((Element) signatures.item(0))
        );
        assertEquals(
                java.util.List.of("SignedInfo", "SignatureValue", "KeyInfo"),
                directChildNames((Element) signatures.item(1))
        );
        assertEquals(
                java.util.List.of(
                        "CanonicalizationMethod",
                        "SignatureMethod",
                        "Reference"
                ),
                directChildNames((Element) document.getElementsByTagNameNS(
                        DSIG_NAMESPACE,
                        "SignedInfo"
                ).item(0))
        );
        assertEquals(
                java.util.List.of("KeyValue", "X509Data"),
                directChildNames((Element) document.getElementsByTagNameNS(
                        DSIG_NAMESPACE,
                        "KeyInfo"
                ).item(0))
        );
        assertTrue(xml.contains("</Documento>\n<Signature"));
        assertTrue(xml.contains("</SetDTE>\n<Signature"));
        assertFalse(xml.contains("\r"));
        assertFalse(xml.contains("&#13;"));
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
    void signsSetDteAfterDocumentoAndPreservesBothSignatures() throws Exception {
        Fixture fixture = fixture();
        DomXmlSignerAdapter signer = signer();
        XmlSignerPort.SignedXml signedDocument =
                signer.sign(request(unsignedXml(), fixture.descriptor()));

        XmlSignerPort.SignedXml signedEnvelope = signer.sign(new XmlSignerPort.SigningRequest(
                signedDocument.xml(),
                "SetDTE-test",
                XmlSignerPort.SignatureTarget.SET_DTE,
                fixture.descriptor(),
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        ));

        Document document = parse(signedEnvelope.xml());
        Element setDte = (Element) document
                .getElementsByTagNameNS(DomDteXmlBuilderAdapter.SII_NAMESPACE, "SetDTE")
                .item(0);
        Element envelopeSignature = nextElement(setDte);
        assertEquals("#SetDTE-test", signedEnvelope.referenceUri());
        assertEquals(XmlSignerPort.SignatureTarget.SET_DTE, signedEnvelope.target());
        assertEquals(DSIG_NAMESPACE, envelopeSignature.getNamespaceURI());
        assertEquals("Signature", envelopeSignature.getLocalName());
        assertEquals(2, document.getElementsByTagNameNS(DSIG_NAMESPACE, "Signature").getLength());
        assertEquals(
                "#DTE-105",
                ((Element) document.getElementsByTagNameNS(
                        DSIG_NAMESPACE,
                        "Reference"
                ).item(0)).getAttribute("URI")
        );
        assertEquals(
                "#SetDTE-test",
                ((Element) document.getElementsByTagNameNS(
                        DSIG_NAMESPACE,
                        "Reference"
                ).item(1)).getAttribute("URI")
        );
        String xml = new String(signedEnvelope.xml(), StandardCharsets.ISO_8859_1);
        assertTrue(xml.contains("</SetDTE>\n<Signature"));
        assertFalse(xml.contains("\r"));
        assertFalse(xml.contains("&#13;"));
        assertChainedSignedInfoNamespaces(document);
        assertBase64LineWidth64(document, "SignatureValue");
        assertBase64LineWidth64(document, "Modulus");
        assertBase64LineWidth64(document, "Exponent");
        assertBase64LineWidth64(document, "X509Certificate");
    }

    @Test
    void rejectsSetDteWithoutSignedDocumento() throws Exception {
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
                XmlSignerPort.XmlSigningFailureReason.INVALID_STRUCTURE,
                exception.getReason()
        );
    }

    @Test
    void rejectsSetDteWhenDocumentoSignatureWasAltered() throws Exception {
        Fixture fixture = fixture();
        DomXmlSignerAdapter signer = signer();
        XmlSignerPort.SignedXml signedDocument =
                signer.sign(request(unsignedXml(), fixture.descriptor()));
        String tampered = new String(
                signedDocument.xml(),
                StandardCharsets.ISO_8859_1
        ).replace("Piñón &amp; engranaje", "Piñón &amp; engranaje alterado");
        XmlSignerPort.SigningRequest request = new XmlSignerPort.SigningRequest(
                tampered.getBytes(StandardCharsets.ISO_8859_1),
                "SetDTE-test",
                XmlSignerPort.SignatureTarget.SET_DTE,
                fixture.descriptor(),
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );

        XmlSignerPort.XmlSigningException exception = assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> signer.sign(request)
        );

        assertEquals(
                XmlSignerPort.XmlSigningFailureReason.SIGNATURE_INVALID,
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
                <EnvioDTE xmlns="http://www.sii.cl/SiiDte" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.sii.cl/SiiDte EnvioDTE_v10.xsd" version="1.0"><SetDTE ID="SetDTE-test"><Caratula/><DTE version="1.0"><Documento ID="DTE-105"><Encabezado/><TED version="1.0"><DD><IT1>Piñón &amp; engranaje</IT1></DD><FRMT algoritmo="SHA1withRSA">signed</FRMT></TED><TmstFirma>2026-07-24T12:30:45</TmstFirma></Documento></DTE></SetDTE></EnvioDTE>
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

    private void assertDocumentoSignedInfoNamespaces(Document document) {
        Element signedInfo = (Element) document.getElementsByTagNameNS(
                DSIG_NAMESPACE,
                "SignedInfo"
        ).item(0);
        assertEquals(
                DSIG_NAMESPACE,
                signedInfo.getAttributeNS(
                        XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                        XMLConstants.XMLNS_ATTRIBUTE
                )
        );
        assertFalse(signedInfo.hasAttributeNS(
                XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                "xsi"
        ));
    }

    private void assertChainedSignedInfoNamespaces(Document document) {
        var signedInfos = document.getElementsByTagNameNS(
                DSIG_NAMESPACE,
                "SignedInfo"
        );
        assertEquals(2, signedInfos.getLength());
        Element documentSignedInfo = (Element) signedInfos.item(0);
        Element envelopeSignedInfo = (Element) signedInfos.item(1);
        assertEquals(
                DSIG_NAMESPACE,
                documentSignedInfo.getAttributeNS(
                        XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                        XMLConstants.XMLNS_ATTRIBUTE
                )
        );
        assertFalse(documentSignedInfo.hasAttributeNS(
                XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                "xsi"
        ));
        assertEquals(
                DSIG_NAMESPACE,
                envelopeSignedInfo.getAttributeNS(
                        XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                        XMLConstants.XMLNS_ATTRIBUTE
                )
        );
        assertEquals(
                DomDteXmlBuilderAdapter.XSI_NAMESPACE,
                envelopeSignedInfo.getAttributeNS(
                        XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                        "xsi"
                )
        );
    }

    private void assertBase64LineWidth64(Document document, String localName) {
        var elements = document.getElementsByTagNameNS(DSIG_NAMESPACE, localName);
        for (int elementIndex = 0; elementIndex < elements.getLength(); elementIndex++) {
            String value = elements.item(elementIndex).getTextContent();
            assertFalse(value.contains("\r"));
            String[] lines = value.split("\n");
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                String line = lines[lineIndex];
                assertTrue(
                        line.length() <= 64,
                        localName + " contains a Base64 line longer than 64 characters"
                );
                if (lineIndex < lines.length - 1) {
                    assertEquals(
                            64,
                            line.length(),
                            localName + " contains an intermediate Base64 line shorter than 64"
                    );
                }
            }
        }
    }

    private Element firstElement(Element parent) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private java.util.List<String> directChildNames(Element parent) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                names.add(child.getLocalName());
            }
        }
        return names;
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
