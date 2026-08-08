package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiDteReconciliationPort;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiiDteReconciliationClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsQueryEstDteAvWithDocumentoSignatureAndParsesReceivedResult() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dte-status", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = """
                    <?xml version="1.0" encoding="ISO-8859-1"?>
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      <soapenv:Body><getEstDteAvResponse>
                        <getEstDteAvReturn>&lt;RESPUESTA&gt;&lt;RESP_HDR&gt;&lt;ESTADO&gt;0&lt;/ESTADO&gt;&lt;GLOSA&gt;OK&lt;/GLOSA&gt;&lt;/RESP_HDR&gt;&lt;RESP_BODY&gt;&lt;RECIBIDO&gt;SI&lt;/RECIBIDO&gt;&lt;ESTADO&gt;DOK&lt;/ESTADO&gt;&lt;GLOSA&gt;Documento Recibido&lt;/GLOSA&gt;&lt;TRACKID&gt;253772832&lt;/TRACKID&gt;&lt;NUMATENCION&gt;123&lt;/NUMATENCION&gt;&lt;/RESP_BODY&gt;&lt;/RESPUESTA&gt;</getEstDteAvReturn>
                      </getEstDteAvResponse></soapenv:Body>
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
        endpoints.setDteStatusUrl(URI.create(
                "http://localhost:" + server.getAddress().getPort() + "/dte-status"
        ));
        properties.setCertification(endpoints);
        byte[] signedXml = """
                <EnvioDTE xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                  <DTE><Documento ID="F33T1"><ds:Signature><ds:SignatureValue>
                    DOCUMENTO_SIGNATURE
                  </ds:SignatureValue></ds:Signature></Documento></DTE>
                  <ds:Signature><ds:SignatureValue>ENVIO_SIGNATURE</ds:SignatureValue></ds:Signature>
                </EnvioDTE>
                """.getBytes(StandardCharsets.ISO_8859_1);

        SiiDteReconciliationPort.ReconciliationResult result =
                new SiiDteReconciliationClient(HttpClient.newHttpClient(), properties).query(
                        new SiiDteReconciliationPort.ReconciliationRequest(
                                "CERTIFICATION", "76184688", "4", "60803000", "K",
                                33, 189, LocalDate.of(2026, 8, 7), 1190,
                                signedXml, "TOKEN123"
                        )
                );

        assertEquals(true, result.received());
        assertEquals("DOK", result.documentStatus());
        assertEquals(253772832L, result.trackId());
        assertEquals("Documento Recibido", result.glosa());
        assertTrue(requestBody.get().contains("http://DefaultNamespace"));
        assertTrue(requestBody.get().contains(">07-08-2026</FechaEmisionDte>"));
        assertTrue(requestBody.get().contains(">DOCUMENTO_SIGNATURE</FirmaDte>"));
        assertTrue(requestBody.get().contains(">TOKEN123</Token>"));
    }
}
