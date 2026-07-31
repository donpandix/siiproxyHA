package cl.cesarg.siiproxyHA.infrastructure.sii;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class SiiXmlSupport {

    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    private SiiXmlSupport() {}

    static Document newDocument() {
        try {
            return builder().newDocument();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create XML document", exception);
        }
    }

    static Document parse(byte[] bytes) {
        try {
            return builder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("SII returned malformed XML", exception);
        }
    }

    static Document parse(String xml) {
        try {
            return builder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("SII returned malformed embedded XML", exception);
        }
    }

    static byte[] soap(String namespace, String operation, LinkedHashMap<String, String> parameters) {
        Document document = newDocument();
        Element envelope = document.createElementNS(SOAP, "soapenv:Envelope");
        envelope.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xsi", XSI);
        envelope.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xsd", XSD);
        document.appendChild(envelope);
        envelope.appendChild(document.createElementNS(SOAP, "soapenv:Header"));
        Element body = document.createElementNS(SOAP, "soapenv:Body");
        envelope.appendChild(body);
        Element method = document.createElementNS(namespace, "m:" + operation);
        method.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:m", namespace);
        body.appendChild(method);
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            Element value = document.createElement(parameter.getKey());
            value.setAttributeNS(XSI, "xsi:type", "xsd:string");
            value.setTextContent(parameter.getValue());
            method.appendChild(value);
        }
        return serialize(document, StandardCharsets.ISO_8859_1.name());
    }

    static Document embeddedOrOuter(byte[] response, String returnElement) {
        Document outer = parse(response);
        String embedded = firstText(outer, returnElement);
        if (embedded != null && embedded.stripLeading().startsWith("<")) {
            return parse(embedded);
        }
        return outer;
    }

    static String firstText(Document document, String localName) {
        NodeList namespaced = document.getElementsByTagNameNS("*", localName);
        if (namespaced.getLength() > 0) {
            return trimmed(namespaced.item(0));
        }
        NodeList plain = document.getElementsByTagName(localName);
        if (plain.getLength() > 0) {
            return trimmed(plain.item(0));
        }
        return null;
    }

    static byte[] serialize(Document document, String encoding) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize XML", exception);
        }
    }

    private static DocumentBuilderFactory factory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static javax.xml.parsers.DocumentBuilder builder() throws Exception {
        var builder = factory().newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        return builder;
    }

    private static String trimmed(Node node) {
        String text = node == null ? null : node.getTextContent();
        return text == null ? null : text.trim();
    }
}
