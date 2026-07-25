package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the unsigned EnvioDTE tree with namespace-aware, secure DOM APIs.
 */
@Component
public class DomDteXmlBuilderAdapter implements DteXmlBuilderPort {

    public static final String SII_NAMESPACE = "http://www.sii.cl/SiiDte";
    public static final String XSI_NAMESPACE = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    public static final String XML_ENCODING = "ISO-8859-1";
    private static final DateTimeFormatter SII_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public BuiltDteXml build(BuildRequest request) {
        Objects.requireNonNull(request, "request is required");
        try {
            validateTedBytes(request);
            Document document = newDocumentBuilder().newDocument();
            String setDteId = "SetDTE-" + request.dteId();
            String documentoId = "DTE-" + request.document().folio();

            Element envioDte = element(document, "EnvioDTE");
            envioDte.setAttributeNS(
                    XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                    XMLConstants.XMLNS_ATTRIBUTE,
                    SII_NAMESPACE
            );
            envioDte.setAttributeNS(
                    XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                    "xmlns:xsi",
                    XSI_NAMESPACE
            );
            envioDte.setAttribute("version", "1.0");
            document.appendChild(envioDte);

            Element setDte = child(envioDte, "SetDTE");
            setDte.setAttribute("ID", setDteId);
            setDte.setIdAttribute("ID", true);

            appendCaratula(setDte, request);
            Element dte = child(setDte, "DTE");
            dte.setAttribute("version", "1.0");
            Element documento = child(dte, "Documento");
            documento.setAttribute("ID", documentoId);
            documento.setIdAttribute("ID", true);

            appendEncabezado(documento, request);
            appendItems(documento, request.items());
            appendReferences(documento, request.references());
            documento.appendChild(importTed(document, request.ted().tedXml()));
            textChild(
                    documento,
                    "TmstFirma",
                    request.ted().generatedAt().format(SII_TIMESTAMP)
            );

            byte[] xml = serialize(document);
            byte[] serializedDd = extractDd(xml);
            try {
                if (!Arrays.equals(serializedDd, request.ted().ddXml())) {
                    throw failure(
                            BuildFailureReason.DD_CHANGED,
                            "DOM serialization changed the signed DD bytes"
                    );
                }
            } finally {
                Arrays.fill(serializedDd, (byte) 0);
            }
            return new BuiltDteXml(xml, documentoId, setDteId, XML_ENCODING);
        } catch (DteXmlBuildException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DteXmlBuildException(
                    BuildFailureReason.SERIALIZATION_FAILURE,
                    "Unable to build EnvioDTE XML",
                    exception
            );
        }
    }

    private void appendCaratula(Element setDte, BuildRequest request) {
        IssuerData issuer = request.issuer();
        DocumentData document = request.document();
        Element caratula = child(setDte, "Caratula");
        caratula.setAttribute("version", "1.0");
        textChild(caratula, "RutEmisor", issuer.rutEmisor());
        textChild(caratula, "RutEnvia", issuer.rutEnvia());
        textChild(caratula, "RutReceptor", request.receiver().rut());
        textChild(caratula, "FchResol", issuer.resolutionDate().toString());
        textChild(caratula, "NroResol", Integer.toString(issuer.resolutionNumber()));
        textChild(
                caratula,
                "TmstFirmaEnv",
                request.ted().generatedAt().format(SII_TIMESTAMP)
        );
        Element subtotal = child(caratula, "SubTotDTE");
        textChild(subtotal, "TpoDTE", Integer.toString(document.tipoDte()));
        textChild(subtotal, "NroDTE", "1");
    }

    private void appendEncabezado(Element documento, BuildRequest request) {
        IssuerData issuer = request.issuer();
        ReceiverData receiver = request.receiver();
        DocumentData document = request.document();

        Element encabezado = child(documento, "Encabezado");
        Element idDoc = child(encabezado, "IdDoc");
        textChild(idDoc, "TipoDTE", Integer.toString(document.tipoDte()));
        textChild(idDoc, "Folio", Long.toString(document.folio()));
        textChild(idDoc, "FchEmis", document.emissionDate().toString());

        Element emisor = child(encabezado, "Emisor");
        textChild(emisor, "RUTEmisor", issuer.rutEmisor());
        textChild(emisor, "RznSoc", issuer.razonSocial());
        textChild(emisor, "GiroEmis", issuer.giro());
        textChild(emisor, "Acteco", issuer.acteco());
        textChild(emisor, "DirOrigen", issuer.direccion());
        textChild(emisor, "CmnaOrigen", issuer.comuna());

        Element receptor = child(encabezado, "Receptor");
        textChild(receptor, "RUTRecep", receiver.rut());
        textChild(receptor, "RznSocRecep", receiver.razonSocial());
        textChild(receptor, "GiroRecep", receiver.giro());
        textChild(receptor, "DirRecep", receiver.direccion());
        textChild(receptor, "CmnaRecep", receiver.comuna());

        Element totales = child(encabezado, "Totales");
        textChild(totales, "MntNeto", nullableNumber(document.netAmount()));
        textChild(
                totales,
                "TasaIVA",
                document.vatRate() == null ? "19" : normalizeNumber(document.vatRate())
        );
        textChild(totales, "IVA", nullableNumber(document.vatAmount()));
        textChild(totales, "MntTotal", Long.toString(document.totalAmount()));
    }

    private void appendItems(Element documento, List<ItemData> items) {
        for (ItemData item : items) {
            Element detalle = child(documento, "Detalle");
            textChild(detalle, "NroLinDet", nullableNumber(item.lineNumber()));
            textChild(detalle, "NmbItem", item.name());
            if (!item.description().isEmpty()) {
                textChild(detalle, "DscItem", item.description());
            }
            if (item.quantity() != null) {
                textChild(
                        detalle,
                        "QtyItem",
                        normalizeNumber(BigDecimal.valueOf(item.quantity()))
                );
            }
            if (item.unitPrice() != null) {
                textChild(
                        detalle,
                        "PrcItem",
                        normalizeNumber(BigDecimal.valueOf(item.unitPrice()))
                );
            }
            textChild(detalle, "MontoItem", nullableNumber(item.amount()));
        }
    }

    private void appendReferences(Element documento, List<ReferenceData> references) {
        for (ReferenceData reference : references) {
            Element element = child(documento, "Referencia");
            textChild(element, "NroLinRef", nullableNumber(reference.lineNumber()));
            textChild(element, "TpoDocRef", reference.documentType());
            if (!reference.folio().isEmpty()) {
                textChild(element, "FolioRef", reference.folio());
            }
            if (reference.date() != null) {
                textChild(element, "FchRef", reference.date().toString());
            }
            if (!reference.code().isEmpty()) {
                textChild(element, "CodRef", reference.code());
            }
            if (!reference.reason().isEmpty()) {
                textChild(element, "RazonRef", reference.reason());
            }
        }
    }

    private Element importTed(Document target, byte[] tedXml) throws Exception {
        Document source;
        try {
            InputSource input = new InputSource(new ByteArrayInputStream(tedXml));
            input.setEncoding(XML_ENCODING);
            source = newDocumentBuilder().parse(input);
        } catch (DteXmlBuildException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DteXmlBuildException(
                    BuildFailureReason.INVALID_TED,
                    "TED XML cannot be parsed safely",
                    exception
            );
        }
        Element root = source.getDocumentElement();
        if (!"TED".equals(localName(root))
                || !"1.0".equals(root.getAttribute("version"))
                || directChildren(root, "DD").size() != 1
                || directChildren(root, "FRMT").size() != 1) {
            throw failure(BuildFailureReason.INVALID_TED, "TED structure is invalid");
        }
        Element frmt = directChildren(root, "FRMT").getFirst();
        if (!"SHA1withRSA".equals(frmt.getAttribute("algoritmo"))) {
            throw failure(BuildFailureReason.INVALID_TED, "TED FRMT profile is invalid");
        }
        return copyToSiiNamespace(target, root);
    }

    private Element copyToSiiNamespace(Document target, Element source) {
        if (source.getPrefix() != null
                || (source.getNamespaceURI() != null && !source.getNamespaceURI().isBlank())) {
            throw failure(
                    BuildFailureReason.UNSUPPORTED_XML,
                    "TED must not declare a foreign element namespace"
            );
        }

        Element copy = target.createElementNS(SII_NAMESPACE, source.getTagName());
        NamedNodeMap attributes = source.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (attribute.getName().startsWith("xmlns")
                    || (attribute.getNamespaceURI() != null
                    && !attribute.getNamespaceURI().isBlank())) {
                throw failure(
                        BuildFailureReason.UNSUPPORTED_XML,
                        "TED contains unsupported namespaced attributes"
                );
            }
            copy.setAttribute(attribute.getName(), attribute.getValue());
        }

        for (Node node = source.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element childElement) {
                copy.appendChild(copyToSiiNamespace(target, childElement));
            } else if (node.getNodeType() == Node.TEXT_NODE
                    || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                copy.appendChild(target.createTextNode(node.getNodeValue()));
            } else {
                throw failure(
                        BuildFailureReason.UNSUPPORTED_XML,
                        "TED contains unsupported XML nodes"
                );
            }
        }
        return copy;
    }

    private void validateTedBytes(BuildRequest request) {
        byte[] extracted = extractDd(request.ted().tedXml());
        try {
            if (!Arrays.equals(extracted, request.ted().ddXml())) {
                throw failure(
                        BuildFailureReason.INVALID_TED,
                        "TED does not contain the declared DD bytes"
                );
            }
        } finally {
            Arrays.fill(extracted, (byte) 0);
        }
    }

    private byte[] extractDd(byte[] xml) {
        byte[] opening = "<DD>".getBytes(StandardCharsets.ISO_8859_1);
        byte[] closing = "</DD>".getBytes(StandardCharsets.ISO_8859_1);
        int start = indexOf(xml, opening, 0);
        if (start < 0 || indexOf(xml, opening, start + opening.length) >= 0) {
            throw failure(BuildFailureReason.INVALID_TED, "TED must contain one DD element");
        }
        int end = indexOf(xml, closing, start + opening.length);
        if (end < 0 || indexOf(xml, closing, end + closing.length) >= 0) {
            throw failure(BuildFailureReason.INVALID_TED, "TED must contain one DD closing tag");
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

    private Element element(Document document, String name) {
        return document.createElementNS(SII_NAMESPACE, name);
    }

    private Element child(Element parent, String name) {
        Element child = element(parent.getOwnerDocument(), name);
        parent.appendChild(child);
        return child;
    }

    private void textChild(Element parent, String name, String value) {
        Element child = child(parent, name);
        child.setTextContent(value == null ? "" : value);
    }

    private String nullableNumber(Number value) {
        return value == null ? "" : value.toString();
    }

    private String normalizeNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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

    private String localName(Element element) {
        String localName = element.getLocalName();
        return (localName == null ? element.getTagName() : localName)
                .toUpperCase(Locale.ROOT);
    }

    private DteXmlBuildException failure(BuildFailureReason reason, String message) {
        return new DteXmlBuildException(reason, message);
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
