package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.port.DteXmlValidatorPort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Validates SII schemas, TED/FRMT and the complete XMLDSig chain.
 */
@Component
public class ComprehensiveDteXmlValidatorAdapter implements DteXmlValidatorPort {

    private static final String SII_NAMESPACE = DomDteXmlBuilderAdapter.SII_NAMESPACE;
    private static final String DSIG_NAMESPACE = XMLSignature.XMLNS;
    private static final String SECURE_VALIDATION_PROPERTY =
            "org.jcp.xml.dsig.secureValidation";
    private static final int MAX_XML_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ISSUES = 100;

    private final Schema dteSchema;
    private final Schema envioDteSchema;

    public ComprehensiveDteXmlValidatorAdapter() {
        dteSchema = loadSchema("DTE_v10.xsd");
        envioDteSchema = loadSchema("EnvioDTE_v10.xsd");
    }

    @Override
    public ValidationResult validate(ValidationRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        byte[] xml = request.xml();
        if (xml.length > MAX_XML_BYTES) {
            issues.add(error(
                    "XML_TOO_LARGE",
                    "XML exceeds the supported validation size",
                    null
            ));
            return new ValidationResult(issues);
        }

        Document document;
        try {
            document = parse(xml);
        } catch (Exception exception) {
            issues.add(error("XML_PARSE", "XML cannot be parsed safely", null));
            return new ValidationResult(issues);
        }

        validateSchema(xml, request.profile(), issues);
        validateIds(document, issues);
        validateAmountProfiles(document, issues);
        validateTedSignatures(document, xml, issues);
        validateXmlSignatures(document, request.profile(), issues);
        Arrays.fill(xml, (byte) 0);
        return new ValidationResult(issues);
    }

    private void validateSchema(
            byte[] xml,
            ValidationProfile profile,
            List<ValidationIssue> issues
    ) {
        Schema schema = profile == ValidationProfile.ENVIO_DTE
                ? envioDteSchema
                : dteSchema;
        Validator validator = schema.newValidator();
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new CollectingErrorHandler(issues));
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException exception) {
            addSchemaIssue(issues, exception);
        } catch (Exception exception) {
            issues.add(error(
                    "XSD_VALIDATION",
                    "XML schema validation could not be completed",
                    null
            ));
        }
    }

    private void validateIds(Document document, List<ValidationIssue> issues) {
        List<String> ids = new ArrayList<>();
        var elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (!element.hasAttribute("ID")) {
                continue;
            }
            String id = element.getAttribute("ID");
            if (id.isBlank() || ids.contains(id)) {
                issues.add(error(
                        "ID_NOT_UNIQUE",
                        "Every ID must be present and globally unique",
                        id.isBlank() ? null : "#" + id
                ));
            } else {
                ids.add(id);
                element.setIdAttribute("ID", true);
            }
        }
    }

    private void validateAmountProfiles(
            Document document,
            List<ValidationIssue> issues
    ) {
        var documentos = document.getElementsByTagNameNS(SII_NAMESPACE, "Documento");
        for (int index = 0; index < documentos.getLength(); index++) {
            Element documento = (Element) documentos.item(index);
            try {
                Element encabezado = requireDirectChild(
                        documento,
                        SII_NAMESPACE,
                        "Encabezado"
                );
                Element idDoc = requireDirectChild(encabezado, SII_NAMESPACE, "IdDoc");
                if (!"33".equals(
                        requireDirectChild(
                                idDoc,
                                SII_NAMESPACE,
                                "TipoDTE"
                        ).getTextContent()
                )) {
                    continue;
                }
                if (!directChildren(
                        documento,
                        SII_NAMESPACE,
                        "DscRcgGlobal"
                ).isEmpty()) {
                    continue;
                }

                List<Element> details = directChildren(
                        documento,
                        SII_NAMESPACE,
                        "Detalle"
                );
                if (details.isEmpty() || hasExemptDetail(details)) {
                    continue;
                }

                long detailTotal = 0;
                for (Element detail : details) {
                    detailTotal = Math.addExact(
                            detailTotal,
                            Long.parseLong(requireDirectChild(
                                    detail,
                                    SII_NAMESPACE,
                                    "MontoItem"
                            ).getTextContent())
                    );
                }

                Element totals = requireDirectChild(
                        encabezado,
                        SII_NAMESPACE,
                        "Totales"
                );
                Element grossIndicator = optionalDirectChild(
                        idDoc,
                        SII_NAMESPACE,
                        "MntBruto"
                );
                String expectedElement = grossIndicator == null
                        ? "MntNeto"
                        : "MntTotal";
                long expected = Long.parseLong(requireDirectChild(
                        totals,
                        SII_NAMESPACE,
                        expectedElement
                ).getTextContent());
                if (detailTotal != expected) {
                    issues.add(error(
                            "DTE_AMOUNT_PROFILE",
                            "Detalle MontoItem sum does not match " + expectedElement,
                            reference(documento)
                    ));
                }
            } catch (Exception exception) {
                issues.add(error(
                        "DTE_AMOUNT_PROFILE",
                        "DTE amount profile cannot be validated",
                        reference(documento)
                ));
            }
        }
    }

    private boolean hasExemptDetail(List<Element> details) {
        for (Element detail : details) {
            if (optionalDirectChild(detail, SII_NAMESPACE, "IndExe") != null) {
                return true;
            }
        }
        return false;
    }

    private void validateTedSignatures(
            Document document,
            byte[] xml,
            List<ValidationIssue> issues
    ) {
        List<byte[]> rawDdElements = extractElements(xml, "<DD>", "</DD>");
        var tedElements = document.getElementsByTagNameNS(SII_NAMESPACE, "TED");
        if (rawDdElements.size() != tedElements.getLength()) {
            issues.add(error(
                    "TED_DD_STRUCTURE",
                    "Each TED must contain one serializable DD",
                    null
            ));
            clear(rawDdElements);
            return;
        }

        for (int index = 0; index < tedElements.getLength(); index++) {
            Element ted = (Element) tedElements.item(index);
            String referenceUri = documentoReference(ted);
            try {
                Element dd = requireDirectChild(ted, SII_NAMESPACE, "DD");
                Element frmt = requireDirectChild(ted, SII_NAMESPACE, "FRMT");
                if (!"SHA1withRSA".equals(frmt.getAttribute("algoritmo"))) {
                    throw new IllegalArgumentException("Unsupported FRMT profile");
                }
                RSAPublicKey publicKey = cafPublicKey(dd);
                byte[] signatureBytes = Base64.getMimeDecoder()
                        .decode(frmt.getTextContent());
                try {
                    Signature verifier = Signature.getInstance("SHA1withRSA");
                    verifier.initVerify(publicKey);
                    verifier.update(rawDdElements.get(index));
                    if (!verifier.verify(signatureBytes)) {
                        throw new IllegalArgumentException("FRMT mismatch");
                    }
                } finally {
                    Arrays.fill(signatureBytes, (byte) 0);
                }
            } catch (Exception exception) {
                issues.add(error(
                        "TED_FRMT_INVALID",
                        "TED FRMT does not validate against its CAF public key",
                        referenceUri
                ));
            }
        }
        clear(rawDdElements);
    }

    private RSAPublicKey cafPublicKey(Element dd) throws Exception {
        Element caf = requireDirectChild(dd, SII_NAMESPACE, "CAF");
        Element da = requireDirectChild(caf, SII_NAMESPACE, "DA");
        Element rsa = requireDirectChild(da, SII_NAMESPACE, "RSAPK");
        byte[] modulus = Base64.getMimeDecoder()
                .decode(requireDirectChild(rsa, SII_NAMESPACE, "M").getTextContent());
        byte[] exponent = Base64.getMimeDecoder()
                .decode(requireDirectChild(rsa, SII_NAMESPACE, "E").getTextContent());
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(
                            new BigInteger(1, modulus),
                            new BigInteger(1, exponent)
                    )
            );
        } finally {
            Arrays.fill(modulus, (byte) 0);
            Arrays.fill(exponent, (byte) 0);
        }
    }

    private void validateXmlSignatures(
            Document document,
            ValidationProfile profile,
            List<ValidationIssue> issues
    ) {
        if (profile == ValidationProfile.DTE_DOCUMENT) {
            validateDteElement(document.getDocumentElement(), null, issues);
            return;
        }

        Element root = document.getDocumentElement();
        if (!isElement(root, SII_NAMESPACE, "EnvioDTE")) {
            issues.add(error(
                    "ENVIO_STRUCTURE",
                    "ENVIO_DTE profile requires an EnvioDTE root",
                    null
            ));
            return;
        }
        String schemaLocation = root.getAttributeNS(
                DomDteXmlBuilderAdapter.XSI_NAMESPACE,
                "schemaLocation"
        );
        if (!DomDteXmlBuilderAdapter.ENVIO_DTE_SCHEMA_LOCATION.equals(schemaLocation)) {
            issues.add(error(
                    "SCHEMA_LOCATION",
                    "EnvioDTE must declare the supported SII schema location",
                    null
            ));
        }
        List<Element> setDtes = directChildren(root, SII_NAMESPACE, "SetDTE");
        List<Element> envelopeSignatures =
                directChildren(root, DSIG_NAMESPACE, "Signature");
        if (setDtes.size() != 1
                || envelopeSignatures.size() != 1
                || nextElement(setDtes.getFirst()) != envelopeSignatures.getFirst()) {
            issues.add(error(
                    "SETDTE_SIGNATURE_STRUCTURE",
                    "SetDTE must be followed by exactly one Signature",
                    setDtes.isEmpty() ? null : reference(setDtes.getFirst())
            ));
            return;
        }

        Element setDte = setDtes.getFirst();
        String signerRut = requireSignerRut(setDte, issues);
        for (Element dte : directChildren(setDte, SII_NAMESPACE, "DTE")) {
            validateDteElement(dte, signerRut, issues);
        }
        validateSignature(
                setDte,
                envelopeSignatures.getFirst(),
                signerRut,
                issues
        );
    }

    private String requireSignerRut(
            Element setDte,
            List<ValidationIssue> issues
    ) {
        try {
            Element caratula = requireDirectChild(setDte, SII_NAMESPACE, "Caratula");
            return RutUtils.normalizeAndValidate(
                    requireDirectChild(
                            caratula,
                            SII_NAMESPACE,
                            "RutEnvia"
                    ).getTextContent(),
                    "RutEnvia"
            );
        } catch (Exception exception) {
            issues.add(error(
                    "SIGNER_AUTHORIZATION",
                    "Caratula must identify a valid RutEnvia",
                    reference(setDte)
            ));
            return null;
        }
    }

    private void validateDteElement(
            Element dte,
            String signerRut,
            List<ValidationIssue> issues
    ) {
        if (!isElement(dte, SII_NAMESPACE, "DTE")) {
            issues.add(error(
                    "DTE_STRUCTURE",
                    "DTE_DOCUMENT profile requires a DTE root",
                    null
            ));
            return;
        }
        List<Element> documentos = directChildren(dte, SII_NAMESPACE, "Documento");
        List<Element> signatures = directChildren(dte, DSIG_NAMESPACE, "Signature");
        if (documentos.size() != 1
                || signatures.size() != 1
                || nextElement(documentos.getFirst()) != signatures.getFirst()) {
            issues.add(error(
                    "DOCUMENTO_SIGNATURE_STRUCTURE",
                    "Documento must be followed by exactly one Signature",
                    documentos.isEmpty() ? null : reference(documentos.getFirst())
            ));
            return;
        }
        validateSignature(
                documentos.getFirst(),
                signatures.getFirst(),
                signerRut,
                issues
        );
    }

    private void validateSignature(
            Element signedElement,
            Element signatureElement,
            String signerRut,
            List<ValidationIssue> issues
    ) {
        String expectedUri = reference(signedElement);
        try {
            if (expectedUri == null) {
                throw new IllegalArgumentException("Signed element has no ID");
            }
            signedElement.setIdAttribute("ID", true);
            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            EmbeddedKey embeddedKey = embeddedKey(signatureElement);
            if (!signerAuthorized(embeddedKey.certificate(), signerRut)) {
                issues.add(error(
                        "SIGNER_AUTHORIZATION",
                        "Signing certificate subject does not match RutEnvia",
                        expectedUri
                ));
                return;
            }
            DOMValidateContext context = new DOMValidateContext(
                    embeddedKey.certificate().getPublicKey(),
                    signatureElement
            );
            context.setProperty(SECURE_VALIDATION_PROPERTY, Boolean.FALSE);
            restrictDereferencing(factory, context, expectedUri);
            XMLSignature signature = factory.unmarshalXMLSignature(context);
            validateKeyInfo(signature.getKeyInfo(), embeddedKey);
            if (!usesLegacyProfile(signature, expectedUri)
                    || !signature.validate(context)) {
                throw new IllegalArgumentException("XMLDSig mismatch");
            }
        } catch (Exception exception) {
            issues.add(error(
                    "XML_SIGNATURE_INVALID",
                    "XMLDSig signature or reference is invalid",
                    expectedUri
            ));
        }
    }

    private boolean signerAuthorized(
            X509Certificate certificate,
            String expectedSignerRut
    ) {
        if (expectedSignerRut == null) {
            return true;
        }
        try {
            String certificateRut = RutUtils.normalizeAndValidate(
                    CertUtils.extractRutFromPrincipal(
                            certificate.getSubjectX500Principal()
                    ),
                    "certificateSubjectRut"
            );
            return expectedSignerRut.equals(certificateRut);
        } catch (Exception exception) {
            return false;
        }
    }

    private EmbeddedKey embeddedKey(Element signatureElement) throws Exception {
        var certificates = signatureElement.getElementsByTagNameNS(
                DSIG_NAMESPACE,
                "X509Certificate"
        );
        if (certificates.getLength() != 1) {
            throw new IllegalArgumentException("Signature must contain one certificate");
        }
        byte[] encoded = Base64.getMimeDecoder().decode(
                certificates.item(0).getTextContent()
        );
        try {
            X509Certificate certificate = (X509Certificate)
                    java.security.cert.CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(encoded));
            certificate.checkValidity();
            if (!(certificate.getPublicKey() instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalArgumentException("Certificate key must be RSA");
            }
            boolean[] usage = certificate.getKeyUsage();
            if (usage != null && (usage.length == 0 || !usage[0])) {
                throw new IllegalArgumentException("Certificate disallows signatures");
            }
            return new EmbeddedKey(certificate, rsaPublicKey);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private void validateKeyInfo(KeyInfo keyInfo, EmbeddedKey embeddedKey) throws Exception {
        if (keyInfo == null
                || keyInfo.getContent().size() != 2
                || !(keyInfo.getContent().get(0) instanceof KeyValue keyValue)
                || !(keyInfo.getContent().get(1) instanceof X509Data)) {
            throw new IllegalArgumentException("KeyInfo structure is invalid");
        }
        PublicKey keyValueKey = keyValue.getPublicKey();
        if (!MessageDigest.isEqual(
                keyValueKey.getEncoded(),
                embeddedKey.publicKey().getEncoded()
        )) {
            throw new IllegalArgumentException("KeyValue and certificate differ");
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
                && Transform.ENVELOPED.equals(
                        ((Transform) reference.getTransforms().getFirst()).getAlgorithm()
                );
    }

    private void restrictDereferencing(
            XMLSignatureFactory factory,
            DOMValidateContext context,
            String expectedUri
    ) {
        var defaultDereferencer = factory.getURIDereferencer();
        context.setURIDereferencer((reference, cryptoContext) -> {
            if (!expectedUri.equals(reference.getURI())) {
                throw new URIReferenceException("Only the expected fragment is allowed");
            }
            return defaultDereferencer.dereference(reference, cryptoContext);
        });
    }

    private Schema loadSchema(String rootSchema) {
        try {
            SchemaFactory factory =
                    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver(new ClasspathSchemaResolver());
            Source source = schemaSource(rootSchema);
            return factory.newSchema(source);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to initialize local SII schema " + rootSchema,
                    exception
            );
        }
    }

    private Source schemaSource(String name) {
        InputStream input = schemaStream(name);
        StreamSource source = new StreamSource(input);
        source.setSystemId("classpath:/xsd/" + name);
        return source;
    }

    private InputStream schemaStream(String name) {
        InputStream input = getClass().getResourceAsStream("/xsd/" + name);
        if (input == null) {
            throw new IllegalStateException("Missing local SII schema " + name);
        }
        return input;
    }

    private Document parse(byte[] xml) throws Exception {
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
        return builder.parse(new ByteArrayInputStream(xml));
    }

    private Element requireDirectChild(
            Element parent,
            String namespace,
            String localName
    ) {
        List<Element> matches = directChildren(parent, namespace, localName);
        if (matches.size() != 1) {
            throw new IllegalArgumentException(
                    parent.getLocalName() + " must contain one " + localName
            );
        }
        return matches.getFirst();
    }

    private Element optionalDirectChild(
            Element parent,
            String namespace,
            String localName
    ) {
        List<Element> matches = directChildren(parent, namespace, localName);
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    parent.getLocalName() + " contains duplicate " + localName
            );
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private List<Element> directChildren(
            Element parent,
            String namespace,
            String localName
    ) {
        List<Element> matches = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element
                    && isElement(element, namespace, localName)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private boolean isElement(Element element, String namespace, String localName) {
        return namespace.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    private Element nextElement(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return sibling;
            }
        }
        return null;
    }

    private String reference(Element element) {
        String id = element.getAttribute("ID");
        return id.isBlank() ? null : "#" + id;
    }

    private String documentoReference(Element descendant) {
        for (Node node = descendant; node != null; node = node.getParentNode()) {
            if (node instanceof Element element
                    && isElement(element, SII_NAMESPACE, "Documento")) {
                return reference(element);
            }
        }
        return null;
    }

    private List<byte[]> extractElements(byte[] xml, String opening, String closing) {
        byte[] startToken = opening.getBytes(StandardCharsets.ISO_8859_1);
        byte[] endToken = closing.getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> elements = new ArrayList<>();
        int fromIndex = 0;
        while (fromIndex < xml.length) {
            int start = indexOf(xml, startToken, fromIndex);
            if (start < 0) {
                break;
            }
            int end = indexOf(xml, endToken, start + startToken.length);
            if (end < 0) {
                break;
            }
            int afterEnd = end + endToken.length;
            elements.add(Arrays.copyOfRange(xml, start, afterEnd));
            fromIndex = afterEnd;
        }
        return elements;
    }

    private int indexOf(byte[] source, byte[] target, int fromIndex) {
        for (int index = fromIndex; index <= source.length - target.length; index++) {
            boolean match = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return index;
            }
        }
        return -1;
    }

    private void clear(List<byte[]> values) {
        values.forEach(value -> Arrays.fill(value, (byte) 0));
    }

    private void addSchemaIssue(
            List<ValidationIssue> issues,
            SAXParseException exception
    ) {
        if (issues.size() >= MAX_ISSUES) {
            return;
        }
        String location = exception.getLineNumber() > 0
                ? " at line " + exception.getLineNumber()
                + ", column " + exception.getColumnNumber()
                : "";
        issues.add(error(
                "XSD_VALIDATION",
                "XML does not conform to the SII schema" + location,
                null
        ));
    }

    private ValidationIssue error(String code, String message, String referenceUri) {
        return new ValidationIssue(
                code,
                message,
                ValidationSeverity.ERROR,
                referenceUri
        );
    }

    private record EmbeddedKey(
            X509Certificate certificate,
            RSAPublicKey publicKey
    ) {
    }

    private class ClasspathSchemaResolver implements LSResourceResolver {

        @Override
        public LSInput resolveResource(
                String type,
                String namespaceUri,
                String publicId,
                String systemId,
                String baseUri
        ) {
            if (systemId == null) {
                return null;
            }
            String name = systemId.substring(systemId.lastIndexOf('/') + 1);
            if (!List.of(
                    "DTE_v10.xsd",
                    "SiiTypes_v10.xsd",
                    "xmldsignature_v10.xsd"
            ).contains(name)) {
                return null;
            }
            return new SchemaInput(publicId, systemId, schemaStream(name));
        }
    }

    private record SchemaInput(
            String publicId,
            String systemId,
            InputStream byteStream
    ) implements LSInput {

        @Override public java.io.Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(java.io.Reader value) { }
        @Override public InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(InputStream value) { }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String value) { }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String value) { }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String value) { }
        @Override public String getBaseURI() { return "classpath:/xsd/"; }
        @Override public void setBaseURI(String value) { }
        @Override public String getEncoding() { return "ISO-8859-1"; }
        @Override public void setEncoding(String value) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean value) { }
    }

    private class CollectingErrorHandler implements ErrorHandler {

        private final List<ValidationIssue> issues;

        private CollectingErrorHandler(List<ValidationIssue> issues) {
            this.issues = issues;
        }

        @Override
        public void warning(SAXParseException exception) {
            if (issues.size() < MAX_ISSUES) {
                issues.add(new ValidationIssue(
                        "XSD_WARNING",
                        "SII schema reported a warning",
                        ValidationSeverity.WARNING,
                        null
                ));
            }
        }

        @Override
        public void error(SAXParseException exception) {
            addSchemaIssue(issues, exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            addSchemaIssue(issues, exception);
            throw exception;
        }
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
