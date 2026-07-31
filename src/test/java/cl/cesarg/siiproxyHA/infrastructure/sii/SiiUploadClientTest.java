package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiUploadPort;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiiUploadClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsExpectedMultipartCookieAndParsesTrackId() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> cookie = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> acceptLanguage = new AtomicReference<>();
        AtomicReference<String> cacheControl = new AtomicReference<>();
        AtomicReference<String> referer = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.ISO_8859_1
            ));
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            acceptLanguage.set(exchange.getRequestHeaders().getFirst("Accept-Language"));
            cacheControl.set(exchange.getRequestHeaders().getFirst("Cache-Control"));
            referer.set(exchange.getRequestHeaders().getFirst("Referer"));
            byte[] response = """
                    <?xml version="1.0"?>
                    <RECEPCIONDTE>
                      <STATUS>0</STATUS>
                      <TRACKID>253515328</TRACKID>
                    </RECEPCIONDTE>
                    """.getBytes(StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().set("Content-Type", "text/xml");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        SiiUploadClient client = new SiiUploadClient(
                HttpClient.newHttpClient(),
                properties("/upload")
        );
        byte[] xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><EnvioDTE/>"
                .getBytes(StandardCharsets.ISO_8859_1);
        SiiUploadPort.UploadResult result = client.upload(
                new SiiUploadPort.UploadRequest(
                        "CERTIFICATION",
                        xml,
                        "TOKEN123",
                        "10438332",
                        "7",
                        "76184688",
                        "4",
                        "envio.xml"
                )
        );

        assertTrue(result.received());
        assertEquals(253515328L, result.trackId());
        assertEquals("TOKEN=TOKEN123", cookie.get());
        assertTrue(userAgent.get().contains("PROG 1.0"));
        assertEquals("es-cl", acceptLanguage.get());
        assertEquals("no-cache", cacheControl.get());
        assertEquals(
                "http://localhost:" + server.getAddress().getPort() + "/",
                referer.get()
        );
        assertNotNull(requestBody.get());
        assertTrue(requestBody.get().contains("name=\"rutSender\"\r\n\r\n10438332"));
        assertTrue(requestBody.get().contains("name=\"dvCompany\"\r\n\r\n4"));
        assertTrue(requestBody.get().contains("name=\"archivo\"; filename=\"envio.xml\""));
        assertTrue(requestBody.get().contains("<EnvioDTE/>"));
    }

    @Test
    void identifiesLegacyHtmlResponseWithoutTreatingItAsXml() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> {
            byte[] response = """
                    <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
                    <html><body>HA OCURRIDO UN ERROR EN EL UPLOAD</body></html>
                    """.getBytes(StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        SiiUploadPort.UploadResult result = new SiiUploadClient(
                HttpClient.newHttpClient(),
                properties("/upload")
        ).upload(new SiiUploadPort.UploadRequest(
                "CERTIFICATION",
                "<EnvioDTE/>".getBytes(StandardCharsets.ISO_8859_1),
                "TOKEN123",
                "10438332",
                "7",
                "76184688",
                "4",
                "envio.xml"
        ));

        assertEquals(200, result.httpStatus());
        assertEquals(null, result.status());
        assertEquals(null, result.trackId());
        assertTrue(!result.received());
        assertEquals(
                "SII upload returned HTML instead of the expected XML response",
                result.reason()
        );
    }

    @Test
    void preservesUnparseableResponseWithoutReportingReception() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> {
            byte[] response = "not-xml".getBytes(StandardCharsets.ISO_8859_1);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        SiiUploadPort.UploadResult result = new SiiUploadClient(
                HttpClient.newHttpClient(),
                properties("/upload")
        ).upload(new SiiUploadPort.UploadRequest(
                "CERTIFICATION",
                "<EnvioDTE/>".getBytes(StandardCharsets.ISO_8859_1),
                "TOKEN123",
                "10438332",
                "7",
                "76184688",
                "4",
                "envio.xml"
        ));

        assertEquals(200, result.httpStatus());
        assertEquals(null, result.trackId());
        assertTrue(!result.received());
        assertEquals("not-xml", new String(result.rawResponse(), StandardCharsets.ISO_8859_1));
    }

    private SiiProperties properties(String path) {
        SiiProperties properties = new SiiProperties();
        properties.setRequestTimeout(Duration.ofSeconds(5));
        SiiProperties.Endpoints endpoints = new SiiProperties.Endpoints();
        endpoints.setUploadUrl(URI.create(
                "http://localhost:" + server.getAddress().getPort() + path
        ));
        properties.setCertification(endpoints);
        return properties;
    }
}
