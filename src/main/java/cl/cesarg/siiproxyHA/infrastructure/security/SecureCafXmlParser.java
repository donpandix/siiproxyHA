package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort.CafFailureReason;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort.CafMaterialUnavailableException;
import cl.cesarg.siiproxyHA.domain.port.CafParserPort;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Parses CAF XML with external entities and external resources disabled.
 */
@Component
public class SecureCafXmlParser implements CafParserPort {

    private static final String RSA = "RSA";
    private static final byte[] KEY_MATCH_CHALLENGE =
            "siiproxyHA-caf-key-check".getBytes(StandardCharsets.UTF_8);

    private final long maxCafSize;

    public SecureCafXmlParser(@Value("${storage.max-caf-size:1048576}") long maxCafSize) {
        if (maxCafSize <= 0) {
            throw new IllegalArgumentException("maxCafSize must be positive");
        }
        this.maxCafSize = maxCafSize;
    }

    @Override
    public ParsedCaf parse(byte[] authorizationXml) {
        return parseInternal(authorizationXml).parsedCaf();
    }

    ParsedAuthorization parsePrivateMaterial(byte[] authorizationXml) {
        return parseInternal(authorizationXml);
    }

    private ParsedAuthorization parseInternal(byte[] authorizationXml) {
        if (authorizationXml == null || authorizationXml.length == 0) {
            throw invalid("CAF XML is required");
        }
        if (authorizationXml.length > maxCafSize) {
            throw invalid("CAF XML exceeds the configured size limit");
        }

        try {
            Document document = newDocumentBuilder().parse(
                    new ByteArrayInputStream(authorizationXml)
            );
            Element root = document.getDocumentElement();
            Element cafElement;
            Element privateKeyElement = null;

            if ("CAF".equals(localName(root))) {
                cafElement = root;
            } else if ("AUTORIZACION".equals(localName(root))) {
                cafElement = requireSingleDirectChild(root, "CAF");
                List<Element> privateKeys = directChildren(root, "RSASK");
                if (privateKeys.size() > 1) {
                    throw invalid("CAF authorization contains multiple RSASK elements");
                }
                if (!privateKeys.isEmpty()) {
                    privateKeyElement = privateKeys.getFirst();
                }
            } else {
                throw invalid("CAF root must be AUTORIZACION or CAF");
            }

            Element da = requireSingleDirectChild(cafElement, "DA");
            String rutEmisor = requiredText(da, "RE");
            int tipoDte = positiveInt(requiredText(da, "TD"), "TD");
            Element range = requireSingleDirectChild(da, "RNG");
            long folioDesde = positiveLong(requiredText(range, "D"), "D");
            long folioHasta = positiveLong(requiredText(range, "H"), "H");
            if (folioHasta < folioDesde) {
                throw invalid("CAF folio range is invalid");
            }
            LocalDate authorizationDate = parseDate(requiredText(da, "FA"));

            Element rsaPublicKeyElement = requireSingleDirectChild(da, "RSAPK");
            BigInteger modulus = positiveBigInteger(requiredText(rsaPublicKeyElement, "M"), "M");
            BigInteger exponent = positiveBigInteger(requiredText(rsaPublicKeyElement, "E"), "E");

            Element frma = requireSingleDirectChild(cafElement, "FRMA");
            String algorithm = frma.getAttribute("algoritmo");
            if (!"SHA1withRSA".equalsIgnoreCase(algorithm == null ? "" : algorithm.trim())) {
                throw invalid("CAF FRMA must use SHA1withRSA");
            }
            decodeBase64(requiredElementText(frma), "FRMA");

            byte[] publicCafXml = serializeElement(cafElement);
            PrivateKey privateKey = privateKeyElement == null
                    ? null
                    : parseAndVerifyPrivateKey(
                            requiredElementText(privateKeyElement),
                            modulus,
                            exponent
                    );

            ParsedCaf parsed = new ParsedCaf(
                    rutEmisor,
                    tipoDte,
                    folioDesde,
                    folioHasta,
                    authorizationDate,
                    publicCafXml,
                    privateKey != null
            );
            return new ParsedAuthorization(parsed, privateKey);
        } catch (CafMaterialUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CafMaterialUnavailableException(
                    CafFailureReason.INVALID_XML,
                    "Unable to parse CAF XML",
                    exception
            );
        }
    }

    private DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ThrowingErrorHandler());
        return builder;
    }

    private byte[] serializeElement(Element element) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            transformer.transform(new DOMSource(element), new StreamResult(output));
            return output.toByteArray();
        }
    }

    private PrivateKey parseAndVerifyPrivateKey(
            String pem,
            BigInteger modulus,
            BigInteger exponent
    ) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object pemObject = parser.readObject();
            if (pemObject == null || parser.readObject() != null) {
                throw invalid("CAF RSASK must contain exactly one private key");
            }

            PrivateKeyInfo privateKeyInfo;
            if (pemObject instanceof PEMKeyPair keyPair) {
                privateKeyInfo = keyPair.getPrivateKeyInfo();
            } else if (pemObject instanceof PrivateKeyInfo keyInfo) {
                privateKeyInfo = keyInfo;
            } else {
                throw invalid("CAF RSASK format is unsupported");
            }

            PrivateKey privateKey = new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
            if (!RSA.equalsIgnoreCase(privateKey.getAlgorithm())) {
                throw invalid("CAF RSASK must use RSA");
            }

            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance(RSA)
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(KEY_MATCH_CHALLENGE);
            byte[] signature = signer.sign();
            try {
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(publicKey);
                verifier.update(KEY_MATCH_CHALLENGE);
                if (!verifier.verify(signature)) {
                    throw new CafMaterialUnavailableException(
                            CafFailureReason.INTEGRITY_FAILURE,
                            "CAF private key does not match RSAPK"
                    );
                }
            } finally {
                Arrays.fill(signature, (byte) 0);
            }
            return privateKey;
        } catch (CafMaterialUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CafMaterialUnavailableException(
                    CafFailureReason.INVALID_XML,
                    "Unable to read CAF private key",
                    exception
            );
        }
    }

    private Element requireSingleDirectChild(Element parent, String name) {
        List<Element> matches = directChildren(parent, name);
        if (matches.size() != 1) {
            throw invalid("CAF must contain exactly one " + name + " element");
        }
        return matches.getFirst();
    }

    private List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                matches.add(element);
            }
        }
        return matches;
    }

    private String requiredText(Element parent, String childName) {
        return requiredElementText(requireSingleDirectChild(parent, childName));
    }

    private String requiredElementText(Element element) {
        String text = element.getTextContent();
        if (text == null || text.isBlank()) {
            throw invalid("CAF element " + localName(element) + " must not be empty");
        }
        return text.trim();
    }

    private int positiveInt(String text, String field) {
        long value = positiveLong(text, field);
        if (value > Integer.MAX_VALUE) {
            throw invalid("CAF field " + field + " is out of range");
        }
        return (int) value;
    }

    private long positiveLong(String text, String field) {
        try {
            long value = Long.parseLong(text);
            if (value <= 0) {
                throw invalid("CAF field " + field + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid("CAF field " + field + " is invalid");
        }
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException exception) {
            throw invalid("CAF authorization date is invalid");
        }
    }

    private BigInteger positiveBigInteger(String text, String field) {
        byte[] decoded = decodeBase64(text, field);
        try {
            BigInteger value = new BigInteger(1, decoded);
            if (value.signum() <= 0) {
                throw invalid("CAF RSA field " + field + " must be positive");
            }
            return value;
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    private byte[] decodeBase64(String text, String field) {
        try {
            return Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        } catch (IllegalArgumentException exception) {
            throw invalid("CAF field " + field + " is not valid Base64");
        }
    }

    private String localName(Element element) {
        String localName = element.getLocalName();
        if (localName != null) {
            return localName.toUpperCase(Locale.ROOT);
        }
        String nodeName = element.getNodeName();
        int separator = nodeName.indexOf(':');
        return (separator >= 0 ? nodeName.substring(separator + 1) : nodeName)
                .toUpperCase(Locale.ROOT);
    }

    private CafMaterialUnavailableException invalid(String message) {
        return new CafMaterialUnavailableException(CafFailureReason.INVALID_XML, message);
    }

    record ParsedAuthorization(ParsedCaf parsedCaf, PrivateKey privateKey) {
    }

    private static class ThrowingErrorHandler implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
