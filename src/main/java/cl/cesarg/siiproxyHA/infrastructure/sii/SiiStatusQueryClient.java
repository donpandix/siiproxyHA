package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiStatusQueryPort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Set;

@Component
public class SiiStatusQueryClient implements SiiStatusQueryPort {

    private final HttpClient httpClient;
    private final SiiProperties properties;

    public SiiStatusQueryClient(HttpClient httpClient, SiiProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public StatusResult query(StatusRequest request) {
        SiiProperties.Endpoints endpoints = properties.endpoints(request.environment());
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("Rut", request.rutCompany());
        parameters.put("Dv", request.dvCompany());
        parameters.put("TrackId", Long.toString(request.trackId()));
        parameters.put("Token", request.token());
        byte[] body = SiiXmlSupport.soap(
                endpoints.getStatusUrl().toString(),
                "getEstUp",
                parameters
        );
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoints.getStatusUrl())
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "text/xml; charset=ISO-8859-1")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new StatusResult(
                        response.statusCode(),
                        null,
                        null,
                        "SII status query returned HTTP " + response.statusCode(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        response.body()
                );
            }
            Document document =
                    SiiXmlSupport.embeddedOrOuter(response.body(), "getEstUpReturn");
            String tokenStatus = SiiXmlSupport.firstText(document, "TOKEN");
            return new StatusResult(
                    response.statusCode(),
                    SiiXmlSupport.firstText(document, "TRACKID"),
                    SiiXmlSupport.firstText(document, "ESTADO"),
                    SiiXmlSupport.firstText(document, "GLOSA"),
                    SiiXmlSupport.firstText(document, "NUM_ATENCION"),
                    integer(document, "INFORMADOS"),
                    integer(document, "ACEPTADOS"),
                    integer(document, "RECHAZADOS"),
                    integer(document, "REPAROS"),
                    tokenStatus != null && Set.of("001", "002", "003").contains(tokenStatus),
                    response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SiiTransportException("SII status query was interrupted", false, exception);
        } catch (java.io.IOException exception) {
            throw new SiiTransportException("SII status query transport failed", false, exception);
        }
    }

    private Integer integer(Document document, String element) {
        String value = SiiXmlSupport.firstText(document, element);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "SII status response contains invalid " + element,
                    exception
            );
        }
    }
}
