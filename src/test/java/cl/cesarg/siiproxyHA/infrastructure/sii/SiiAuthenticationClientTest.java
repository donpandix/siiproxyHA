package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.application.service.SelfSignedCertGenerator;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.infrastructure.security.Pkcs12SigningCredentialResolver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiiAuthenticationClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void signsSeedWithExactCredentialAndCachesToken() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var certificate = SelfSignedCertGenerator.generate(
                "CN=Token Signer,SERIALNUMBER=10438332-7",
                keyPair
        );
        UUID tenantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        var descriptor = new SigningCredentialPort.SigningCredentialDescriptor(
                credentialId,
                tenantId,
                "10438332-7",
                certificate.getSerialNumber().toString(),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
        );
        SigningCredentialPort signingCredentials = mock(SigningCredentialPort.class);
        when(signingCredentials.requireSigningCredential(any())).thenReturn(descriptor);
        Pkcs12SigningCredentialResolver resolver =
                mock(Pkcs12SigningCredentialResolver.class);
        when(resolver.withCredential(any(), any())).thenAnswer(invocation -> {
            Pkcs12SigningCredentialResolver.CredentialOperation<Object> operation =
                    invocation.getArgument(1);
            return operation.execute(keyPair.getPrivate(), certificate);
        });

        AtomicInteger seedCalls = new AtomicInteger();
        AtomicReference<String> signedGetToken = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/seed", exchange -> {
            seedCalls.incrementAndGet();
            respond(exchange, """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      <soapenv:Body><getSeedResponse>
                        <getSeedReturn>&lt;RESPUESTA&gt;&lt;RESP_HDR&gt;&lt;ESTADO&gt;00&lt;/ESTADO&gt;&lt;/RESP_HDR&gt;&lt;RESP_BODY&gt;&lt;SEMILLA&gt;123456&lt;/SEMILLA&gt;&lt;/RESP_BODY&gt;&lt;/RESPUESTA&gt;</getSeedReturn>
                      </getSeedResponse></soapenv:Body>
                    </soapenv:Envelope>
                    """);
        });
        server.createContext("/token", exchange -> {
            Document request = SiiXmlSupport.parse(exchange.getRequestBody().readAllBytes());
            signedGetToken.set(SiiXmlSupport.firstText(request, "pszXml"));
            respond(exchange, """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      <soapenv:Body><getTokenResponse>
                        <getTokenReturn>&lt;RESPUESTA&gt;&lt;RESP_HDR&gt;&lt;ESTADO&gt;00&lt;/ESTADO&gt;&lt;/RESP_HDR&gt;&lt;RESP_BODY&gt;&lt;TOKEN&gt;TOKEN123&lt;/TOKEN&gt;&lt;/RESP_BODY&gt;&lt;/RESPUESTA&gt;</getTokenReturn>
                      </getTokenResponse></soapenv:Body>
                    </soapenv:Envelope>
                    """);
        });
        server.start();

        SiiAuthenticationClient client = new SiiAuthenticationClient(
                HttpClient.newHttpClient(),
                properties(),
                signingCredentials,
                resolver
        );
        var first = client.acquireToken(
                "CERTIFICATION",
                tenantId,
                "10438332-7",
                credentialId
        );
        var second = client.acquireToken(
                "CERTIFICATION",
                tenantId,
                "10438332-7",
                credentialId
        );

        assertEquals("TOKEN123", first.value());
        assertEquals(first, second);
        assertEquals(1, seedCalls.get());
        Document signed = SiiXmlSupport.parse(signedGetToken.get());
        assertEquals("123456", SiiXmlSupport.firstText(signed, "Semilla"));
        DOMValidateContext validation = new DOMValidateContext(
                certificate.getPublicKey(),
                signed.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0)
        );
        validation.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        assertTrue(XMLSignatureFactory.getInstance("DOM")
                .unmarshalXMLSignature(validation)
                .validate(validation));
    }

    private SiiProperties properties() {
        SiiProperties properties = new SiiProperties();
        properties.setRequestTimeout(Duration.ofSeconds(5));
        properties.setTokenTtl(Duration.ofMinutes(55));
        SiiProperties.Endpoints endpoints = new SiiProperties.Endpoints();
        endpoints.setSeedUrl(local("/seed"));
        endpoints.setTokenUrl(local("/token"));
        properties.setCertification(endpoints);
        return properties;
    }

    private URI local(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String xml)
            throws java.io.IOException {
        byte[] response = xml.getBytes(StandardCharsets.ISO_8859_1);
        exchange.getResponseHeaders().set("Content-Type", "text/xml");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
