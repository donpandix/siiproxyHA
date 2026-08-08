package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiDteReconciliationPort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Set;

@Component
public class SiiDteReconciliationClient implements SiiDteReconciliationPort {

    private static final String SERVICE_NAMESPACE = "http://DefaultNamespace";
    private static final DateTimeFormatter SII_DATE = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private final HttpClient httpClient;
    private final SiiProperties properties;

    public SiiDteReconciliationClient(HttpClient httpClient, SiiProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public ReconciliationResult query(ReconciliationRequest request) {
        SiiProperties.Endpoints endpoints = properties.endpoints(request.environment());
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("RutEmpresa", request.rutCompany());
        parameters.put("DvEmpresa", request.dvCompany());
        parameters.put("RutReceptor", request.rutReceiver());
        parameters.put("DvReceptor", request.dvReceiver());
        parameters.put("TipoDte", Integer.toString(request.documentType()));
        parameters.put("FolioDte", Long.toString(request.folio()));
        parameters.put("FechaEmisionDte", request.emissionDate().format(SII_DATE));
        parameters.put("MontoDte", Long.toString(request.total()));
        parameters.put("FirmaDte", documentSignature(request.signedXml()));
        parameters.put("Token", request.token());
        byte[] body = SiiXmlSupport.soap(SERVICE_NAMESPACE, "getEstDteAv", parameters);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoints.getDteStatusUrl())
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "text/xml; charset=ISO-8859-1")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ReconciliationResult(
                        response.statusCode(), null, null, null,
                        "SII DTE reconciliation returned HTTP " + response.statusCode(),
                        null, null, false, response.body()
                );
            }
            Document document = SiiXmlSupport.embeddedOrOuter(response.body(), "getEstDteAvReturn");
            String headerStatus = sectionText(document, "RESP_HDR", "ESTADO");
            String received = sectionText(document, "RESP_BODY", "RECIBIDO");
            String documentStatus = sectionText(document, "RESP_BODY", "ESTADO");
            String glosa = firstNonBlank(
                    sectionText(document, "RESP_BODY", "GLOSA"),
                    sectionText(document, "RESP_HDR", "GLOSA")
            );
            return new ReconciliationResult(
                    response.statusCode(),
                    headerStatus,
                    receivedValue(received),
                    documentStatus,
                    glosa,
                    longValue(sectionText(document, "RESP_BODY", "TRACKID")),
                    sectionText(document, "RESP_BODY", "NUMATENCION"),
                    headerStatus != null && Set.of("1", "2").contains(headerStatus.trim()),
                    response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SiiTransportException("SII DTE reconciliation was interrupted", false, exception);
        } catch (java.io.IOException exception) {
            throw new SiiTransportException("SII DTE reconciliation transport failed", false, exception);
        }
    }

    private String documentSignature(byte[] xml) {
        Document document = SiiXmlSupport.parse(xml);
        NodeList signatures = document.getElementsByTagNameNS(
                "http://www.w3.org/2000/09/xmldsig#",
                "SignatureValue"
        );
        if (signatures.getLength() == 0) {
            throw new IllegalArgumentException("Stored XML has no Documento SignatureValue");
        }
        String signature = signatures.item(0).getTextContent();
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Stored XML has an empty Documento SignatureValue");
        }
        return signature.replaceAll("\\s+", "");
    }

    private String sectionText(Document document, String section, String child) {
        NodeList sections = document.getElementsByTagNameNS("*", section);
        if (sections.getLength() == 0) {
            sections = document.getElementsByTagName(section);
        }
        if (sections.getLength() == 0 || !(sections.item(0) instanceof Element element)) {
            return null;
        }
        NodeList children = element.getElementsByTagNameNS("*", child);
        if (children.getLength() == 0) {
            children = element.getElementsByTagName(child);
        }
        return children.getLength() == 0 ? null : children.item(0).getTextContent().trim();
    }

    private Long longValue(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("SII reconciliation response contains invalid TRACKID", exception);
        }
    }

    private Boolean receivedValue(String value) {
        if (value == null) return null;
        if ("SI".equalsIgnoreCase(value.trim())) return true;
        if ("NO".equalsIgnoreCase(value.trim())) return false;
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
