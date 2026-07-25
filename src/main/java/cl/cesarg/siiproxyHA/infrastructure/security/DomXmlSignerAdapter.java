package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Applies and immediately validates the SII legacy XMLDSig profile.
 */
@Component
public class DomXmlSignerAdapter implements XmlSignerPort {

    private static final String SII_NAMESPACE = DomDteXmlBuilderAdapter.SII_NAMESPACE;
    private static final String XMLDSIG_NAMESPACE = XMLSignature.XMLNS;
    private static final String XML_ENCODING = DomDteXmlBuilderAdapter.XML_ENCODING;
    private static final String SECURE_VALIDATION_PROPERTY =
            "org.jcp.xml.dsig.secureValidation";

    private final Pkcs12SigningCredentialResolver credentialResolver;

    public DomXmlSignerAdapter(Pkcs12SigningCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    @Override
    public SignedXml sign(SigningRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (request.target() != SignatureTarget.DOCUMENTO) {
            throw failure(
                    XmlSigningFailureReason.UNSUPPORTED_TARGET,
                    "Only Documento signing is implemented"
            );
        }

        byte[] input = request.xml();
        byte[] originalDd = extractDd(input);
        try {
            preflight(input, request.referenceId());
            byte[] signed = credentialResolver.withCredential(
                    request.credential(),
                    (privateKey, certificate) -> signAndValidate(
                            input,
                            request.referenceId(),
                            privateKey,
                            certificate,
                            originalDd
                    )
            );
            return new SignedXml(
                    signed,
                    "#" + request.referenceId(),
                    request.target(),
                    request.credential().credentialId(),
                    request.profile()
            );
        } catch (XmlSigningException exception) {
            throw exception;
        } catch (Pkcs12SigningCredentialResolver.CredentialLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XmlSigningException(
                    XmlSigningFailureReason.SIGNING_FAILURE,
                    "Unable to sign Documento XML",
                    exception
            );
        } finally {
            Arrays.fill(input, (byte) 0);
            Arrays.fill(originalDd, (byte) 0);
        }
    }

    private void preflight(byte[] xml, String referenceId) {
        try {
            Document document = parse(xml);
            Element documento = requireDocumento(document, referenceId);
            Element dte = requireParent(documento, "DTE");
            requireNoSignature(dte);
        } catch (XmlSigningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XmlSigningException(
                    XmlSigningFailureReason.INVALID_XML,
                    "Documento XML cannot be parsed safely",
                    exception
            );
        }
    }

    private byte[] signAndValidate(
            byte[] xml,
            String referenceId,
            java.security.PrivateKey privateKey,
            X509Certificate certificate,
            byte[] originalDd
    ) {
        try {
            Document document = parse(xml);
            Element documento = requireDocumento(document, referenceId);
            Element dte = requireParent(documento, "DTE");
            requireNoSignature(dte);
            documento.setIdAttribute("ID", true);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            String referenceUri = "#" + referenceId;
            Reference reference = factory.newReference(
                    referenceUri,
                    factory.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(factory.newTransform(
                            CanonicalizationMethod.INCLUSIVE,
                            (TransformParameterSpec) null
                    )),
                    null,
                    null
            );
            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(
                            CanonicalizationMethod.INCLUSIVE,
                            (C14NMethodParameterSpec) null
                    ),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(reference)
            );
            KeyInfo keyInfo = keyInfo(factory.getKeyInfoFactory(), certificate);
            XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo);

            Node nextSibling = documento.getNextSibling();
            DOMSignContext context = nextSibling == null
                    ? new DOMSignContext(privateKey, dte)
                    : new DOMSignContext(privateKey, dte, nextSibling);
            restrictDereferencing(factory, context, referenceUri);
            signature.sign(context);

            byte[] output = serialize(document);
            verifyDd(output, originalDd);
            validateSerialized(output, referenceId, certificate);
            return output;
        } catch (XmlSigningException exception) {
            throw exception;
        } catch (MarshalException | XMLSignatureException exception) {
            throw new XmlSigningException(
                    XmlSigningFailureReason.SIGNING_FAILURE,
                    "XMLDSig generation failed",
                    exception
            );
        } catch (Exception exception) {
            throw new XmlSigningException(
                    XmlSigningFailureReason.INVALID_XML,
                    "Documento XML cannot be processed safely",
                    exception
            );
        }
    }

    private KeyInfo keyInfo(
            KeyInfoFactory factory,
            X509Certificate certificate
    ) throws Exception {
        KeyValue keyValue = factory.newKeyValue(certificate.getPublicKey());
        X509Data x509Data = factory.newX509Data(List.of(certificate));
        return factory.newKeyInfo(List.of(keyValue, x509Data));
    }

    private void validateSerialized(
            byte[] xml,
            String referenceId,
            X509Certificate certificate
    ) throws Exception {
        Document document = parse(xml);
        Element documento = requireDocumento(document, referenceId);
        Element dte = requireParent(documento, "DTE");
        List<Element> signatures = directChildren(dte, XMLDSIG_NAMESPACE, "Signature");
        if (signatures.size() != 1 || nextElement(documento) != signatures.getFirst()) {
            throw failure(
                    XmlSigningFailureReason.INVALID_STRUCTURE,
                    "Documento Signature must be its immediate element sibling"
            );
        }
        documento.setIdAttribute("ID", true);

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext context = new DOMValidateContext(
                certificate.getPublicKey(),
                signatures.getFirst()
        );
        context.setProperty(SECURE_VALIDATION_PROPERTY, Boolean.FALSE);
        String expectedUri = "#" + referenceId;
        restrictDereferencing(factory, context, expectedUri);
        XMLSignature signature = factory.unmarshalXMLSignature(context);
        if (signature.getSignedInfo().getReferences().size() != 1
                || !expectedUri.equals(
                        ((Reference) signature.getSignedInfo().getReferences().getFirst()).getURI()
                )
                || !signature.validate(context)) {
            throw failure(
                    XmlSigningFailureReason.SIGNATURE_INVALID,
                    "Serialized Documento signature is invalid"
            );
        }
    }

    private void restrictDereferencing(
            XMLSignatureFactory factory,
            javax.xml.crypto.XMLCryptoContext context,
            String expectedUri
    ) {
        var defaultDereferencer = factory.getURIDereferencer();
        context.setURIDereferencer((reference, cryptoContext) -> {
            if (!expectedUri.equals(reference.getURI())) {
                throw new URIReferenceException("Only the expected internal reference is allowed");
            }
            return defaultDereferencer.dereference(reference, cryptoContext);
        });
    }

    private Element requireDocumento(Document document, String referenceId) {
        List<Element> matches = new ArrayList<>();
        var elements = document.getElementsByTagNameNS(SII_NAMESPACE, "Documento");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (referenceId.equals(element.getAttribute("ID"))) {
                matches.add(element);
            }
        }
        if (matches.isEmpty()) {
            throw failure(
                    XmlSigningFailureReason.TARGET_NOT_FOUND,
                    "Documento reference ID was not found"
            );
        }
        if (matches.size() != 1 || countId(document, referenceId) != 1) {
            throw failure(
                    XmlSigningFailureReason.AMBIGUOUS_TARGET,
                    "Documento reference ID must be unique"
            );
        }
        return matches.getFirst();
    }

    private int countId(Document document, String referenceId) {
        int count = 0;
        var elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (referenceId.equals(element.getAttribute("ID"))) {
                count++;
            }
        }
        return count;
    }

    private Element requireParent(Element element, String expectedName) {
        Node parent = element.getParentNode();
        if (!(parent instanceof Element parentElement)
                || !SII_NAMESPACE.equals(parentElement.getNamespaceURI())
                || !expectedName.equals(parentElement.getLocalName())) {
            throw failure(
                    XmlSigningFailureReason.INVALID_STRUCTURE,
                    "Documento must be a direct child of DTE"
            );
        }
        return parentElement;
    }

    private void requireNoSignature(Element dte) {
        if (!directChildren(dte, XMLDSIG_NAMESPACE, "Signature").isEmpty()) {
            throw failure(
                    XmlSigningFailureReason.ALREADY_SIGNED,
                    "Documento already has a Signature sibling"
            );
        }
    }

    private List<Element> directChildren(
            Element parent,
            String namespace,
            String localName
    ) {
        List<Element> matches = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private Element nextElement(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return sibling;
            }
        }
        return null;
    }

    private Document parse(byte[] xml) throws Exception {
        return newDocumentBuilder().parse(new ByteArrayInputStream(xml));
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
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false
        );
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ThrowingErrorHandler());
        return builder;
    }

    private byte[] serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, XML_ENCODING);
        transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new XmlSigningException(
                    XmlSigningFailureReason.SERIALIZATION_FAILURE,
                    "Signed Documento XML cannot be serialized",
                    exception
            );
        }
    }

    private void verifyDd(byte[] xml, byte[] expectedDd) {
        byte[] actualDd = extractDd(xml);
        try {
            if (!Arrays.equals(expectedDd, actualDd)) {
                throw failure(
                        XmlSigningFailureReason.DD_CHANGED,
                        "Documento signing changed the signed DD bytes"
                );
            }
        } finally {
            Arrays.fill(actualDd, (byte) 0);
        }
    }

    private byte[] extractDd(byte[] xml) {
        byte[] opening = "<DD>".getBytes(StandardCharsets.ISO_8859_1);
        byte[] closing = "</DD>".getBytes(StandardCharsets.ISO_8859_1);
        int start = indexOf(xml, opening, 0);
        int end = start < 0 ? -1 : indexOf(xml, closing, start + opening.length);
        if (start < 0
                || end < 0
                || indexOf(xml, opening, start + opening.length) >= 0
                || indexOf(xml, closing, end + closing.length) >= 0) {
            throw failure(
                    XmlSigningFailureReason.INVALID_STRUCTURE,
                    "Documento XML must contain one DD element"
            );
        }
        return Arrays.copyOfRange(xml, start, end + closing.length);
    }

    private int indexOf(byte[] source, byte[] target, int fromIndex) {
        for (int index = fromIndex; index <= source.length - target.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        return -1;
    }

    private XmlSigningException failure(
            XmlSigningFailureReason reason,
            String message
    ) {
        return new XmlSigningException(reason, message);
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
