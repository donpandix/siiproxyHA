package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiAuthenticationPort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.infrastructure.security.Pkcs12SigningCredentialResolver;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SiiAuthenticationClient implements SiiAuthenticationPort {

    private final HttpClient httpClient;
    private final SiiProperties properties;
    private final SigningCredentialPort signingCredentials;
    private final Pkcs12SigningCredentialResolver credentialResolver;
    private final ConcurrentHashMap<TokenKey, TokenLease> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TokenKey, Object> locks = new ConcurrentHashMap<>();

    public SiiAuthenticationClient(
            HttpClient httpClient,
            SiiProperties properties,
            SigningCredentialPort signingCredentials,
            Pkcs12SigningCredentialResolver credentialResolver
    ) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.signingCredentials = signingCredentials;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public TokenLease acquireToken(
            String environment,
            UUID tenantId,
            String signerRut,
            UUID signingCredentialId
    ) {
        String normalizedEnvironment = environment.trim().toUpperCase(Locale.ROOT);
        TokenKey key = new TokenKey(normalizedEnvironment, tenantId, signerRut, signingCredentialId);
        TokenLease cached = tokens.get(key);
        if (usable(cached)) {
            return cached;
        }

        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            cached = tokens.get(key);
            if (usable(cached)) {
                return cached;
            }
            TokenLease fresh = requestToken(key);
            tokens.put(key, fresh);
            return fresh;
        }
    }

    @Override
    public void invalidateToken(
            String environment,
            UUID tenantId,
            String signerRut,
            UUID signingCredentialId
    ) {
        tokens.remove(new TokenKey(
                environment.trim().toUpperCase(Locale.ROOT),
                tenantId,
                signerRut,
                signingCredentialId
        ));
    }

    private TokenLease requestToken(TokenKey key) {
        SigningCredentialPort.SigningCredentialDescriptor credential =
                signingCredentials.requireSigningCredential(
                        new SigningCredentialPort.SigningCredentialSelector(
                                key.tenantId(),
                                key.signerRut(),
                                key.signingCredentialId()
                        )
                );
        SiiProperties.Endpoints endpoints = properties.endpoints(key.environment());

        byte[] seedResponse = post(
                endpoints.getSeedUrl(),
                SiiXmlSupport.soap(
                        endpoints.getSeedUrl().toString(),
                        "getSeed",
                        new LinkedHashMap<>()
                ),
                false
        );
        Document seedDocument = SiiXmlSupport.embeddedOrOuter(seedResponse, "getSeedReturn");
        requireSuccess(seedDocument, "seed");
        String seed = required(seedDocument, "SEMILLA");

        byte[] signedTokenRequest;
        try {
            signedTokenRequest = credentialResolver.withCredential(
                    credential,
                    (privateKey, certificate) -> signGetToken(seed, privateKey, certificate)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign SII token request", exception);
        }

        LinkedHashMap<String, String> tokenParameters = new LinkedHashMap<>();
        tokenParameters.put(
                "pszXml",
                new String(signedTokenRequest, StandardCharsets.ISO_8859_1)
        );
        byte[] tokenResponse = post(
                endpoints.getTokenUrl(),
                SiiXmlSupport.soap(
                        endpoints.getTokenUrl().toString(),
                        "getToken",
                        tokenParameters
                ),
                false
        );
        Document tokenDocument = SiiXmlSupport.embeddedOrOuter(tokenResponse, "getTokenReturn");
        requireSuccess(tokenDocument, "token");
        String token = required(tokenDocument, "TOKEN");
        if (!token.matches("[A-Za-z0-9]{1,64}")) {
            throw new IllegalArgumentException("SII returned an invalid token format");
        }
        return new TokenLease(
                token,
                key.environment(),
                key.signingCredentialId(),
                OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getTokenTtl())
        );
    }

    private byte[] signGetToken(
            String seed,
            java.security.PrivateKey privateKey,
            java.security.cert.X509Certificate certificate
    ) throws Exception {
        Document document = SiiXmlSupport.newDocument();
        Element root = document.createElement("getToken");
        document.appendChild(root);
        Element item = document.createElement("item");
        root.appendChild(item);
        Element seedElement = document.createElement("Semilla");
        seedElement.setTextContent(seed);
        item.appendChild(seedElement);

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        Reference reference = factory.newReference(
                "",
                factory.newDigestMethod(DigestMethod.SHA1, null),
                List.of(factory.newTransform(Transform.ENVELOPED, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)),
                null,
                null
        );
        SignedInfo signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(
                        CanonicalizationMethod.INCLUSIVE,
                        (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null
                ),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                List.of(reference)
        );
        KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
        var keyInfo = keyInfoFactory.newKeyInfo(List.of(
                keyInfoFactory.newKeyValue(certificate.getPublicKey()),
                keyInfoFactory.newX509Data(List.of(certificate))
        ));
        factory.newXMLSignature(signedInfo, keyInfo)
                .sign(new DOMSignContext(privateKey, root));
        return SiiXmlSupport.serialize(document, StandardCharsets.ISO_8859_1.name());
    }

    private byte[] post(java.net.URI uri, byte[] body, boolean outcomeUnknown) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "text/xml; charset=ISO-8859-1")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "SII authentication returned HTTP " + response.statusCode()
                );
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SiiTransportException("SII authentication was interrupted", outcomeUnknown, exception);
        } catch (java.io.IOException exception) {
            throw new SiiTransportException("SII authentication transport failed", outcomeUnknown, exception);
        }
    }

    private void requireSuccess(Document document, String operation) {
        String status = required(document, "ESTADO");
        if (!"00".equals(status)) {
            String glosa = SiiXmlSupport.firstText(document, "GLOSA");
            throw new IllegalStateException(
                    "SII " + operation + " rejected with status " + status
                            + (glosa == null ? "" : ": " + glosa)
            );
        }
    }

    private String required(Document document, String name) {
        String value = SiiXmlSupport.firstText(document, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SII response does not contain " + name);
        }
        return value;
    }

    private boolean usable(TokenLease token) {
        return token != null
                && token.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2));
    }

    private record TokenKey(
            String environment,
            UUID tenantId,
            String signerRut,
            UUID signingCredentialId
    ) {
        private TokenKey {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(signerRut);
            Objects.requireNonNull(signingCredentialId);
        }
    }
}
