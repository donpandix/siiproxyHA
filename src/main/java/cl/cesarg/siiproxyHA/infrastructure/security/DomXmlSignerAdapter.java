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

        byte[] input = request.xml();
        byte[] originalDd = extractDd(input);
        try {
            preflight(input, request.referenceId(), request.target());
            byte[] signed = credentialResolver.withCredential(
                    request.credential(),
                    (privateKey, certificate) -> signAndValidate(
                            input,
                            request.referenceId(),
                            request.target(),
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
                    "Unable to sign DTE XML",
                    exception
            );
        } finally {
            Arrays.fill(input, (byte) 0);
            Arrays.fill(originalDd, (byte) 0);
        }
    }

    private void preflight(
            byte[] xml,
            String referenceId,
            SignatureTarget target
    ) {
        try {
            Document document = parse(xml);
            TargetNodes nodes = requireTarget(document, referenceId, target);
            requireNoSignature(nodes.parent(), target);
            if (target == SignatureTarget.SET_DTE) {
                requireSignedDocumentos(document, nodes.target());
            } else {
                requireUnsignedEnvelope(nodes.parent());
            }
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
            SignatureTarget target,
            java.security.PrivateKey privateKey,
            X509Certificate certificate,
            byte[] originalDd
    ) {
        try {
            Document document = parse(xml);
            TargetNodes nodes = requireTarget(document, referenceId, target);
            requireNoSignature(nodes.parent(), target);
            if (target == SignatureTarget.SET_DTE) {
                validateDocumentSignatures(document, nodes.target(), certificate);
            } else {
                requireUnsignedEnvelope(nodes.parent());
            }
            nodes.target().setIdAttribute("ID", true);

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

            Node nextSibling = nodes.target().getNextSibling();
            DOMSignContext context = nextSibling == null
                    ? new DOMSignContext(privateKey, nodes.parent())
                    : new DOMSignContext(privateKey, nodes.parent(), nextSibling);
            restrictDereferencing(factory, context, referenceUri);
            signature.sign(context);

            byte[] output = serialize(document);
            verifyDd(output, originalDd);
            validateSerialized(output, referenceId, target, certificate);
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
            SignatureTarget target,
            X509Certificate certificate
    ) throws Exception {
        Document document = parse(xml);
        TargetNodes nodes = requireTarget(document, referenceId, target);
        List<Element> signatures = directChildren(
                nodes.parent(),
                XMLDSIG_NAMESPACE,
                "Signature"
        );
        if (signatures.size() != 1
                || nextElement(nodes.target()) != signatures.getFirst()) {
            throw failure(
                    XmlSigningFailureReason.INVALID_STRUCTURE,
                    target + " Signature must be its immediate element sibling"
            );
        }
        if (target == SignatureTarget.SET_DTE) {
            validateDocumentSignatures(document, nodes.target(), certificate);
        }
        validateSignature(
                nodes.target(),
                signatures.getFirst(),
                "#" + referenceId,
                certificate
        );
    }

    private void validateSignature(
            Element signedElement,
            Element signatureElement,
            String expectedUri,
            X509Certificate certificate
    ) throws Exception {
        signedElement.setIdAttribute("ID", true);
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext context = new DOMValidateContext(
                certificate.getPublicKey(),
                signatureElement
        );
        context.setProperty(SECURE_VALIDATION_PROPERTY, Boolean.FALSE);
        restrictDereferencing(factory, context, expectedUri);
        XMLSignature signature = factory.unmarshalXMLSignature(context);
        if (!usesLegacyProfile(signature, expectedUri) || !signature.validate(context)) {
            throw failure(
                    XmlSigningFailureReason.SIGNATURE_INVALID,
                    "Serialized XML signature is invalid"
            );
        }
    }

    private boolean usesLegacyProfile(XMLSignature signature, String expectedUri) {
        if (!CanonicalizationMethod.INCLUSIVE.equals(
                signature.getSignedInfo().getCanonicalizationMethod().getAlgorithm()
        ) || !SignatureMethod.RSA_SHA1.equals(
                signature.getSignedInfo().getSignatureMethod().getAlgorithm()
        ) || signature.getSignedInfo().getReferences().size() != 1) {
            return false;
        }
        Reference reference =
                (Reference) signature.getSignedInfo().getReferences().getFirst();
        return expectedUri.equals(reference.getURI())
                && DigestMethod.SHA1.equals(reference.getDigestMethod().getAlgorithm())
                && reference.getTransforms().size() == 1
                && CanonicalizationMethod.INCLUSIVE.equals(
                        ((Transform) reference.getTransforms().getFirst()).getAlgorithm()
                );
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

    private TargetNodes requireTarget(
            Document document,
            String referenceId,
            SignatureTarget target
    ) {
        String elementName = target == SignatureTarget.DOCUMENTO
                ? "Documento"
                : "SetDTE";
        String parentName = target == SignatureTarget.DOCUMENTO
                ? "DTE"
                : "EnvioDTE";
        Element element = requireUniqueElement(document, elementName, referenceId);
        return new TargetNodes(element, requireParent(element, parentName));
    }

    private Element requireUniqueElement(
            Document document,
            String elementName,
            String referenceId
    ) {
        List<Element> matches = new ArrayList<>();
        var elements = document.getElementsByTagNameNS(SII_NAMESPACE, elementName);
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (referenceId.equals(element.getAttribute("ID"))) {
                matches.add(element);
            }
        }
        if (matches.isEmpty()) {
            throw failure(
                    XmlSigningFailureReason.TARGET_NOT_FOUND,
                    elementName + " reference ID was not found"
            );
        }
        if (matches.size() != 1 || countId(document, referenceId) != 1) {
            throw failure(
                    XmlSigningFailureReason.AMBIGUOUS_TARGET,
                    elementName + " reference ID must be unique"
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
                    element.getLocalName()
                            + " must be a direct child of "
                            + expectedName
            );
        }
        return parentElement;
    }

    private void requireNoSignature(Element parent, SignatureTarget target) {
        if (!directChildren(parent, XMLDSIG_NAMESPACE, "Signature").isEmpty()) {
            throw failure(
                    XmlSigningFailureReason.ALREADY_SIGNED,
                    target + " already has a Signature sibling"
            );
        }
    }

    private void requireUnsignedEnvelope(Element dte) {
        Element setDte = requireParent(dte, "SetDTE");
        Element envioDte = requireParent(setDte, "EnvioDTE");
        if (!directChildren(envioDte, XMLDSIG_NAMESPACE, "Signature").isEmpty()) {
            throw failure(
                    XmlSigningFailureReason.ALREADY_SIGNED,
                    "Documento cannot be signed inside an already signed SetDTE"
            );
        }
    }

    private List<SignedDocumento> requireSignedDocumentos(
            Document document,
            Element setDte
    ) {
        List<Element> dtes = directChildren(setDte, SII_NAMESPACE, "DTE");
        if (dtes.isEmpty()) {
            throw failure(
                    XmlSigningFailureReason.INVALID_STRUCTURE,
                    "SetDTE must contain at least one DTE"
            );
        }

        List<SignedDocumento> signedDocumentos = new ArrayList<>();
        for (Element dte : dtes) {
            List<Element> documentos = directChildren(dte, SII_NAMESPACE, "Documento");
            List<Element> signatures =
                    directChildren(dte, XMLDSIG_NAMESPACE, "Signature");
            if (documentos.size() != 1
                    || signatures.size() != 1
                    || nextElement(documentos.getFirst()) != signatures.getFirst()) {
                throw failure(
                        XmlSigningFailureReason.INVALID_STRUCTURE,
                        "Each DTE must contain one Documento followed by its Signature"
                );
            }
            Element documento = documentos.getFirst();
            String documentoId = documento.getAttribute("ID");
            if (documentoId.isBlank() || countId(document, documentoId) != 1) {
                throw failure(
                        XmlSigningFailureReason.AMBIGUOUS_TARGET,
                        "Signed Documento ID must be present and unique"
                );
            }
            signedDocumentos.add(new SignedDocumento(
                    documento,
                    signatures.getFirst(),
                    "#" + documentoId
            ));
        }
        return signedDocumentos;
    }

    private void validateDocumentSignatures(
            Document document,
            Element setDte,
            X509Certificate certificate
    ) throws Exception {
        for (SignedDocumento signed : requireSignedDocumentos(document, setDte)) {
            validateSignature(
                    signed.documento(),
                    signed.signature(),
                    signed.referenceUri(),
                    certificate
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
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.writeBytes(
                    DomDteXmlBuilderAdapter.XML_DECLARATION.getBytes(
                            StandardCharsets.ISO_8859_1
                    )
            );
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

    private record TargetNodes(Element target, Element parent) {
    }

    private record SignedDocumento(
            Element documento,
            Element signature,
            String referenceUri
    ) {
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
