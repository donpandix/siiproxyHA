package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.application.service.SelfSignedCertGenerator;
import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.DteXmlValidatorPort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComprehensiveDteXmlValidatorAdapterTest {

    private static final String PASSWORD = "test-password";

    private final ComprehensiveDteXmlValidatorAdapter validator =
            new ComprehensiveDteXmlValidatorAdapter();

    @Test
    void validatesCompleteGeneratedEnvioDte() throws Exception {
        byte[] signedXml = signedEnvioDte();

        DteXmlValidatorPort.ValidationResult result = validate(signedXml);

        assertTrue(result.valid(), () -> result.issues().toString());
        assertTrue(result.issues().isEmpty(), () -> result.issues().toString());
    }

    @Test
    void reportsSchemaFrmtAndXmlSignatureProblems() throws Exception {
        String valid = new String(signedEnvioDte(), StandardCharsets.ISO_8859_1);
        String invalid = valid
                .replaceFirst(
                        "(<EnvioDTE[^>]*) version=\"1.0\"",
                        "$1"
                )
                .replaceFirst(
                        "(<FRMT algoritmo=\"SHA1withRSA\">).",
                        "$1A"
                );

        DteXmlValidatorPort.ValidationResult result = validate(
                invalid.getBytes(StandardCharsets.ISO_8859_1)
        );

        assertTrue(hasCode(result, "XSD_VALIDATION"), () -> result.issues().toString());
        assertTrue(hasCode(result, "TED_FRMT_INVALID"), () -> result.issues().toString());
        assertTrue(hasCode(result, "XML_SIGNATURE_INVALID"), () -> result.issues().toString());
    }

    @Test
    void rejectsEnvioDteWithoutDeclaredSchemaLocation() throws Exception {
        String valid = new String(signedEnvioDte(), StandardCharsets.ISO_8859_1);
        String missingSchemaLocation = valid.replace(
                " xsi:schemaLocation=\""
                        + DomDteXmlBuilderAdapter.ENVIO_DTE_SCHEMA_LOCATION
                        + "\"",
                ""
        );

        DteXmlValidatorPort.ValidationResult result = validate(
                missingSchemaLocation.getBytes(StandardCharsets.ISO_8859_1)
        );

        assertTrue(hasCode(result, "SCHEMA_LOCATION"), () -> result.issues().toString());
    }

    @Test
    void rejectsDoctypeWithoutResolvingExternalEntities() {
        byte[] xml = """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <!DOCTYPE EnvioDTE [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <EnvioDTE xmlns="http://www.sii.cl/SiiDte">&xxe;</EnvioDTE>
                """.getBytes(StandardCharsets.ISO_8859_1);

        DteXmlValidatorPort.ValidationResult result = validate(xml);

        assertEquals("XML_PARSE", result.issues().getFirst().code());
    }

    @Test
    void rejectsCertificateSubjectThatDoesNotMatchRutEnvia() throws Exception {
        String valid = new String(signedEnvioDte(), StandardCharsets.ISO_8859_1);
        String unauthorized = valid.replace(
                "<RutEnvia>10438332-7</RutEnvia>",
                "<RutEnvia>76184688-4</RutEnvia>"
        );

        DteXmlValidatorPort.ValidationResult result = validate(
                unauthorized.getBytes(StandardCharsets.ISO_8859_1)
        );

        assertTrue(
                hasCode(result, "SIGNER_AUTHORIZATION"),
                () -> result.issues().toString()
        );
    }

    private DteXmlValidatorPort.ValidationResult validate(byte[] xml) {
        return validator.validate(new DteXmlValidatorPort.ValidationRequest(
                xml,
                DteXmlValidatorPort.ValidationProfile.ENVIO_DTE
        ));
    }

    private boolean hasCode(
            DteXmlValidatorPort.ValidationResult result,
            String code
    ) {
        return result.issues().stream().anyMatch(issue -> code.equals(issue.code()));
    }

    private byte[] signedEnvioDte() throws Exception {
        CafTestFixtureFactory.Fixture cafFixture = CafTestFixtureFactory.create();
        Caf caf = caf(cafFixture.xml());
        TedGeneratorPort.GeneratedTed ted = ted(caf, cafFixture.xml());
        DteXmlBuilderPort.BuiltDteXml built =
                new DomDteXmlBuilderAdapter().build(buildRequest(ted));

        SigningFixture signing = signingFixture();
        DomXmlSignerAdapter signer = signing.signer();
        XmlSignerPort.SignedXml signedDocument = signer.sign(
                new XmlSignerPort.SigningRequest(
                        built.xml(),
                        built.documentoId(),
                        XmlSignerPort.SignatureTarget.DOCUMENTO,
                        signing.descriptor(),
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );
        return signer.sign(new XmlSignerPort.SigningRequest(
                signedDocument.xml(),
                built.setDteId(),
                XmlSignerPort.SignatureTarget.SET_DTE,
                signing.descriptor(),
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        )).xml();
    }

    private TedGeneratorPort.GeneratedTed ted(Caf caf, byte[] cafXml) throws Exception {
        CafRepository repository = mock(CafRepository.class);
        StoragePort storage = mock(StoragePort.class);
        when(repository.findById(caf.getId())).thenReturn(Optional.of(caf));
        when(storage.get(caf.getCafPath()))
                .thenAnswer(invocation -> Arrays.copyOf(cafXml, cafXml.length));
        SecureCafXmlParser parser = new SecureCafXmlParser(1_048_576);
        TedGeneratorAdapter generator = new TedGeneratorAdapter(
                new CafMaterialAdapter(repository, storage, parser),
                new CafPrivateKeyResolver(repository, storage, parser),
                Clock.fixed(
                        Instant.parse("2026-07-24T16:30:45Z"),
                        ZoneId.of("America/Santiago")
                )
        );
        return generator.generate(new TedGeneratorPort.TedRequest(
                caf.getTenant().getId(),
                CafTestFixtureFactory.RUT_EMISOR,
                33,
                1,
                105,
                caf.getId(),
                LocalDate.of(2026, 7, 24),
                "60803000-K",
                "SERVICIO DE IMPUESTOS INTERNOS",
                119_000,
                "Piñón de prueba"
        ));
    }

    private DteXmlBuilderPort.BuildRequest buildRequest(
            TedGeneratorPort.GeneratedTed ted
    ) {
        return new DteXmlBuilderPort.BuildRequest(
                UUID.randomUUID(),
                new DteXmlBuilderPort.IssuerData(
                        CafTestFixtureFactory.RUT_EMISOR,
                        "10438332-7",
                        "TEST EMISOR",
                        "SERVICIOS INFORMATICOS",
                        "726000",
                        "VINA DEL MAR",
                        "VALPARAISO",
                        LocalDate.of(2014, 8, 22),
                        80
                ),
                new DteXmlBuilderPort.ReceiverData(
                        "60803000-K",
                        "SERVICIO DE IMPUESTOS INTERNOS",
                        "GOBIERNO",
                        "SANTIAGO",
                        "SANTIAGO"
                ),
                new DteXmlBuilderPort.DocumentData(
                        33,
                        105,
                        LocalDate.of(2026, 7, 24),
                        100_000L,
                        new BigDecimal("19"),
                        19_000L,
                        119_000L
                ),
                List.of(new DteXmlBuilderPort.ItemData(
                        1,
                        "Pinon de prueba",
                        "",
                        1.0,
                        100_000.0,
                        100_000L
                )),
                List.of(),
                ted
        );
    }

    private SigningFixture signingFixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = SelfSignedCertGenerator.generate(
                "CN=Integral Validator, SERIALNUMBER=10.438.332-7",
                keyPair
        );
        UserCertificateEntity entity = credentialEntity(certificate);
        byte[] pkcs12 = pkcs12(keyPair, certificate);
        UserCertificateRepository repository = mock(UserCertificateRepository.class);
        CertificateStoragePort storage = mock(CertificateStoragePort.class);
        CryptoService crypto = mock(CryptoService.class);
        when(repository.findByIdAndTenantId(entity.getId(), entity.getTenantId()))
                .thenReturn(Optional.of(entity));
        when(storage.get(entity.getCertificatePath()))
                .thenAnswer(invocation -> Arrays.copyOf(pkcs12, pkcs12.length));
        when(crypto.decrypt("encrypted-password", "iv")).thenReturn(PASSWORD);
        return new SigningFixture(
                new DomXmlSignerAdapter(
                        new Pkcs12SigningCredentialResolver(repository, storage, crypto)
                ),
                descriptor(entity)
        );
    }

    private Caf caf(byte[] authorizationXml) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        Caf caf = new Caf();
        caf.setId(UUID.randomUUID());
        caf.setTenant(tenant);
        caf.setTipoDte(33);
        caf.setPuntoVenta(1);
        caf.setFolioDesde(100L);
        caf.setFolioHasta(110L);
        caf.setCafPath("caf/integral-validator.xml");
        caf.setCafSha256(sha256(authorizationXml));
        caf.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        caf.setFchAutorizacion(LocalDate.of(2026, 1, 1));
        caf.setCreatedAt(Instant.now());
        caf.setActive(true);
        return caf;
    }

    private UserCertificateEntity credentialEntity(X509Certificate certificate) {
        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setRutUsuario("10438332-7");
        entity.setCertSubjectRut("10438332-7");
        entity.setCertificatePath("tenants/test/certs/integral.p12");
        entity.setEncryptedPassword("encrypted-password");
        entity.setEncryptionIv("iv");
        entity.setEncryptionAlgorithm("AES/GCM/NoPadding");
        entity.setCertSerialNumber(certificate.getSerialNumber().toString());
        entity.setValidFrom(OffsetDateTime.ofInstant(
                certificate.getNotBefore().toInstant(),
                ZoneOffset.UTC
        ));
        entity.setValidUntil(OffsetDateTime.ofInstant(
                certificate.getNotAfter().toInstant(),
                ZoneOffset.UTC
        ));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private SigningCredentialPort.SigningCredentialDescriptor descriptor(
            UserCertificateEntity entity
    ) {
        return new SigningCredentialPort.SigningCredentialDescriptor(
                entity.getId(),
                entity.getTenantId(),
                entity.getCertSubjectRut(),
                entity.getCertSerialNumber(),
                entity.getValidFrom(),
                entity.getValidUntil()
        );
    }

    private byte[] pkcs12(
            KeyPair keyPair,
            X509Certificate certificate
    ) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = PASSWORD.toCharArray();
        try {
            keyStore.load(null, password);
            keyStore.setKeyEntry(
                    "signing-key",
                    keyPair.getPrivate(),
                    password,
                    new Certificate[]{certificate}
            );
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                keyStore.store(output, password);
                return output.toByteArray();
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SigningFixture(
            DomXmlSignerAdapter signer,
            SigningCredentialPort.SigningCredentialDescriptor descriptor
    ) {
    }
}
