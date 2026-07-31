package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiStatusQueryPort;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiiStatusQueryClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsQueryEstUpContractAndParsesEmbeddedResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/status", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.ISO_8859_1
            ));
            byte[] response = """
                    <?xml version="1.0" encoding="ISO-8859-1"?>
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      <soapenv:Body>
                        <getEstUpResponse>
                          <getEstUpReturn>&lt;RESPUESTA&gt;&lt;RESP_HDR&gt;&lt;TRACKID&gt;253515328&lt;/TRACKID&gt;&lt;ESTADO&gt;EPR&lt;/ESTADO&gt;&lt;GLOSA&gt;Envio Procesado&lt;/GLOSA&gt;&lt;/RESP_HDR&gt;&lt;RESP_BODY&gt;&lt;INFORMADOS&gt;1&lt;/INFORMADOS&gt;&lt;ACEPTADOS&gt;1&lt;/ACEPTADOS&gt;&lt;RECHAZADOS&gt;0&lt;/RECHAZADOS&gt;&lt;REPAROS&gt;0&lt;/REPAROS&gt;&lt;NUM_ATENCION&gt;12345&lt;/NUM_ATENCION&gt;&lt;/RESP_BODY&gt;&lt;/RESPUESTA&gt;</getEstUpReturn>
                        </getEstUpResponse>
                      </soapenv:Body>
                    </soapenv:Envelope>
                    """.getBytes(StandardCharsets.ISO_8859_1);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        SiiProperties properties = new SiiProperties();
        properties.setRequestTimeout(Duration.ofSeconds(5));
        SiiProperties.Endpoints endpoints = new SiiProperties.Endpoints();
        endpoints.setStatusUrl(URI.create(
                "http://localhost:" + server.getAddress().getPort() + "/status"
        ));
        properties.setCertification(endpoints);
        SiiStatusQueryPort.StatusResult result = new SiiStatusQueryClient(
                HttpClient.newHttpClient(),
                properties
        ).query(new SiiStatusQueryPort.StatusRequest(
                "CERTIFICATION",
                "76184688",
                "4",
                253515328L,
                "TOKEN123"
        ));

        assertEquals("EPR", result.status());
        assertEquals("Envio Procesado", result.glosa());
        assertEquals("12345", result.numeroAtencion());
        assertEquals(1, result.informedCount());
        assertEquals(1, result.acceptedCount());
        assertEquals(0, result.rejectedCount());
        assertEquals(0, result.repairCount());
        assertTrue(requestBody.get().contains("<Rut"));
        assertTrue(requestBody.get().contains(">76184688</Rut>"));
        assertTrue(requestBody.get().contains(">253515328</TrackId>"));
        assertTrue(requestBody.get().contains(">TOKEN123</Token>"));
    }
}
