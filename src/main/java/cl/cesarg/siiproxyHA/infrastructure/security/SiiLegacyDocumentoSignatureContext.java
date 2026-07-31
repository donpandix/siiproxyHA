package cl.cesarg.siiproxyHA.infrastructure.security;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Builds the namespace-neutral Documento view used by the legacy SII validator.
 *
 * <p>The final EnvioDTE remains namespace-aware. Only the cryptographic context
 * for the inner Documento signature excludes namespaces inherited from the
 * EnvioDTE envelope.</p>
 */
final class SiiLegacyDocumentoSignatureContext {

    private SiiLegacyDocumentoSignatureContext() {
    }

    static DetachedDocumento unsigned(Element sourceDocumento) throws Exception {
        return create(sourceDocumento, null);
    }

    static DetachedDocumento signed(
            Element sourceDocumento,
            Element sourceSignature
    ) throws Exception {
        if (sourceSignature == null) {
            throw new IllegalArgumentException("Documento Signature is required");
        }
        return create(sourceDocumento, sourceSignature);
    }

    private static DetachedDocumento create(
            Element sourceDocumento,
            Element sourceSignature
    ) throws Exception {
        if (sourceDocumento == null
                || !"Documento".equals(sourceDocumento.getLocalName())) {
            throw new IllegalArgumentException("Documento element is required");
        }

        Document detachedDocument = newDocument();
        Element detachedDte = detachedDocument.createElement("DTE");
        detachedDocument.appendChild(detachedDte);
        Element detachedDocumento = copyWithoutNamespaces(
                detachedDocument,
                sourceDocumento
        );
        detachedDte.appendChild(detachedDocumento);
        detachedDocumento.setIdAttribute("ID", true);

        Element detachedSignature = null;
        if (sourceSignature != null) {
            detachedDte.appendChild(detachedDocument.createTextNode("\n"));
            detachedSignature = (Element) detachedDocument.importNode(
                    sourceSignature,
                    true
            );
            detachedDte.appendChild(detachedSignature);
        }
        return new DetachedDocumento(
                detachedDocument,
                detachedDte,
                detachedDocumento,
                detachedSignature
        );
    }

    private static Element copyWithoutNamespaces(
            Document targetDocument,
            Element source
    ) {
        String localName = source.getLocalName();
        if (localName == null || localName.isBlank()) {
            throw new IllegalArgumentException(
                    "Documento contains an element without a local name"
            );
        }
        Element copy = targetDocument.createElement(localName);
        copyAttributes(source, copy);

        for (Node node = source.getFirstChild();
             node != null;
             node = node.getNextSibling()) {
            if (node instanceof Element child) {
                copy.appendChild(copyWithoutNamespaces(targetDocument, child));
            } else if (node.getNodeType() == Node.TEXT_NODE) {
                copy.appendChild(targetDocument.createTextNode(node.getNodeValue()));
            } else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
                copy.appendChild(targetDocument.createCDATASection(node.getNodeValue()));
            } else {
                throw new IllegalArgumentException(
                        "Documento contains unsupported XML nodes"
                );
            }
        }
        return copy;
    }

    private static void copyAttributes(Element source, Element target) {
        NamedNodeMap attributes = source.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            String namespace = attribute.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)) {
                continue;
            }
            if (namespace == null || namespace.isBlank()) {
                target.setAttribute(attribute.getName(), attribute.getValue());
                continue;
            }
            if (XMLConstants.XML_NS_URI.equals(namespace)) {
                target.setAttributeNS(
                        namespace,
                        attribute.getName(),
                        attribute.getValue()
                );
                continue;
            }
            throw new IllegalArgumentException(
                    "Documento contains unsupported namespaced attributes"
            );
        }
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().newDocument();
    }

    record DetachedDocumento(
            Document document,
            Element dte,
            Element documento,
            Element signature
    ) {
    }
}
