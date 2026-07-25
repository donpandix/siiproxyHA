package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DteXmlValidatorPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DocumentProcessingHistoryEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DocumentProcessingHistoryRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DocumentoRepositoryAdapter;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.FolioAssignmentRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import cl.cesarg.siiproxyHA.infrastructure.security.CafTestFixtureFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DteEmissionIntegrationTest {

    private static final String POSTGRES_DATABASE = "dte_emission_test";
    private static final String POSTGRES_USER = "dte_emission";
    private static final String POSTGRES_PASSWORD = "dte_emission";
    private static final String MINIO_USER = "integration";
    private static final String MINIO_PASSWORD = "integration-secret";
    private static final String DTE_BUCKET = "integration-dte";
    private static final String CERTIFICATES_BUCKET = "integration-certificates";
    private static final String PKCS12_PASSWORD = "ephemeral-password";
    private static final String MASTER_KEY = "01".repeat(32);

    @Container
    static final GenericContainer<?> postgres = new GenericContainer<>("postgres:17")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", POSTGRES_DATABASE)
            .withEnv("POSTGRES_USER", POSTGRES_USER)
            .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
            .waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("quay.io/minio/minio:latest")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withCommand("server", "/data")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                POSTGRES_DATABASE
        ));
        registry.add("spring.datasource.username", () -> POSTGRES_USER);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("minio.endpoint", () -> "http://%s:%d".formatted(
                minio.getHost(),
                minio.getMappedPort(9000)
        ));
        registry.add("minio.access-key", () -> MINIO_USER);
        registry.add("minio.secret-key", () -> MINIO_PASSWORD);
        registry.add("minio.bucket", () -> DTE_BUCKET);
        registry.add("minio.certificates-bucket", () -> CERTIFICATES_BUCKET);
        registry.add("security.encryption.master-key", () -> MASTER_KEY);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CafService cafService;

    @Autowired
    private UserCertificateService certificateService;

    @Autowired
    private UserCertificateRepository certificateRepository;

    @Autowired
    private DocumentoRepositoryAdapter metadataRepository;

    @Autowired
    private DocumentProcessingHistoryRepository historyRepository;

    @Autowired
    private DteRepository dteRepository;

    @Autowired
    private FolioAssignmentRepository folioAssignmentRepository;

    @Autowired
    private StoragePort storage;

    @Autowired
    private DteXmlValidatorPort xmlValidator;

    @Test
    void emitsSignsStoresDownloadsAndReplaysOneDteIdempotently() throws Exception {
        Tenant tenant = persistTenant();
        CafTestFixtureFactory.Fixture cafFixture = CafTestFixtureFactory.create();
        cafService.create(tenant.getId(), 1, cafFixture.xml(), "integration-caf.xml");
        UUID certificateId = uploadSigningCertificate(tenant.getId());
        UUID documentId = UUID.randomUUID();
        String requestJson = emissionRequest(tenant, documentId);

        JsonNode firstResponse = postDte(requestJson);
        String expectedKey = "dte/2026/07/%s-%d.xml".formatted(
                documentId,
                CafTestFixtureFactory.FOLIO_DESDE
        );

        assertThat(firstResponse.path("documentId").asText()).isEqualTo(documentId.toString());
        assertThat(firstResponse.path("folio").asText())
                .isEqualTo(String.valueOf(CafTestFixtureFactory.FOLIO_DESDE));
        assertThat(firstResponse.path("status").asText()).isEqualTo(DocumentStatus.STORED.name());
        assertThat(firstResponse.path("objectKey").asText()).isEqualTo(expectedKey);
        assertThat(firstResponse.path("attemptCount").asInt()).isEqualTo(1);

        byte[] storedXml = storage.get(expectedKey);
        assertThat(storedXml).isNotEmpty();
        assertThat(sha256(storedXml)).isEqualTo(firstResponse.path("sha256").asText());
        assertThat(firstResponse.path("sizeBytes").asLong()).isEqualTo(storedXml.length);
        assertIntegralXml(storedXml, documentId);

        JsonNode downloaded = objectMapper.readTree(mockMvc.perform(get(
                        "/api/v1/dte/{id}/xml",
                        documentId
                ))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(Base64.getDecoder().decode(downloaded.path("xmlBase64").asText()))
                .isEqualTo(storedXml);

        JsonNode replay = postDte(requestJson);
        assertThat(replay.path("documentId").asText()).isEqualTo(documentId.toString());
        assertThat(replay.path("objectKey").asText()).isEqualTo(expectedKey);
        assertThat(replay.path("sha256").asText()).isEqualTo(sha256(storedXml));
        assertThat(replay.path("attemptCount").asInt()).isEqualTo(1);

        assertPersistenceAndIdempotency(documentId, certificateId, expectedKey);
        mockMvc.perform(get("/api/v1/dte/{id}/status", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(DocumentStatus.STORED.name()))
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.objectKey").value(expectedKey));
    }

    private Tenant persistTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setTenantCode("INT-" + tenant.getId().toString().substring(0, 8));
        tenant.setRutEmisor(CafTestFixtureFactory.RUT_EMISOR);
        tenant.setRazonSocial("EMISOR DE INTEGRACION");
        tenant.setGiro("SERVICIOS DE PRUEBA");
        tenant.setActeco("726000");
        tenant.setDireccion("TEATINOS 120");
        tenant.setComuna("SANTIAGO");
        tenant.setFchResol(LocalDate.of(2014, 8, 22));
        tenant.setNroResol(80);
        tenant.setCreatedAt(Instant.now());
        tenant.setActive(true);
        return tenantRepository.saveAndFlush(tenant);
    }

    private UUID uploadSigningCertificate(UUID tenantId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = SelfSignedCertGenerator.generate(
                "CN=Integration Signer,SERIALNUMBER=" + CafTestFixtureFactory.RUT_EMISOR,
                keyPair
        );
        byte[] pkcs12 = pkcs12(keyPair, certificate);

        return certificateService.uploadCertificate(
                tenantId,
                CafTestFixtureFactory.RUT_EMISOR,
                "Integration Signer",
                "integration-test",
                "integration-signer.p12",
                new ByteArrayInputStream(pkcs12),
                pkcs12.length,
                "application/x-pkcs12",
                PKCS12_PASSWORD,
                true
        ).getId();
    }

    private byte[] pkcs12(KeyPair keyPair, X509Certificate certificate) throws Exception {
        char[] password = PKCS12_PASSWORD.toCharArray();
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            keyStore.setKeyEntry(
                    "integration-signer",
                    keyPair.getPrivate(),
                    password,
                    new Certificate[]{certificate}
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            keyStore.store(output, password);
            return output.toByteArray();
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private String emissionRequest(Tenant tenant, UUID documentId) throws Exception {
        Map<String, Object> receptor = Map.of(
                "rutReceptor", "60803000-K",
                "razonSocial", "SERVICIO DE IMPUESTOS INTERNOS",
                "giro", "SERVICIO PUBLICO",
                "email", "contacto@sii.cl",
                "telefono", "223951000",
                "direccion", "TEATINOS 120",
                "comuna", "SANTIAGO",
                "ciudad", "SANTIAGO"
        );
        Map<String, Object> item = Map.of(
                "nroLinDet", 1,
                "nmbItem", "Servicio de integración",
                "qtyItem", 1,
                "unmdItem", "UN",
                "prcItem", 7000,
                "montoItem", 7000
        );
        return objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("id", documentId.toString()),
                Map.entry("tenantId", tenant.getId().toString()),
                Map.entry("tenantCode", tenant.getTenantCode()),
                Map.entry("rutEnvia", CafTestFixtureFactory.RUT_EMISOR),
                Map.entry("tipoDte", CafTestFixtureFactory.TIPO_DTE),
                Map.entry("fchEmis", "2026-07-25"),
                Map.entry("receptor", receptor),
                Map.entry("items", java.util.List.of(item)),
                Map.entry("mntNeto", 7000),
                Map.entry("iva", 1330),
                Map.entry("mntTotal", 8330)
        ));
    }

    private JsonNode postDte(String requestJson) throws Exception {
        byte[] response = mockMvc.perform(post("/api/v1/dte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return objectMapper.readTree(response);
    }

    private void assertIntegralXml(byte[] xml, UUID documentId) {
        DteXmlValidatorPort.ValidationResult validation = xmlValidator.validate(
                new DteXmlValidatorPort.ValidationRequest(
                        xml,
                        DteXmlValidatorPort.ValidationProfile.ENVIO_DTE
                )
        );
        assertThat(validation.issues()).isEmpty();

        String encoded = new String(xml, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(encoded).contains("<EnvioDTE");
        assertThat(encoded).contains("<FRMT algoritmo=\"SHA1withRSA\">");
        assertThat(encoded).contains("URI=\"#DTE-" + CafTestFixtureFactory.FOLIO_DESDE + "\"");
        assertThat(encoded).contains("URI=\"#SetDTE-" + documentId + "\"");
        assertThat(encoded).contains("SignatureMethod Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\"");
    }

    private void assertPersistenceAndIdempotency(UUID documentId,
                                                 UUID certificateId,
                                                 String expectedKey) {
        assertThat(dteRepository.count()).isEqualTo(1);
        assertThat(folioAssignmentRepository.count()).isEqualTo(1);

        var metadata = metadataRepository.findByDocumentId(documentId.toString()).orElseThrow();
        assertThat(metadata.getStatus()).isEqualTo(DocumentStatus.STORED);
        assertThat(metadata.getObjectKey()).isEqualTo(expectedKey);
        assertThat(metadata.getAttemptCount()).isEqualTo(1);

        var transitions = historyRepository.findByDocumentIdOrderByCreatedAtAsc(
                documentId.toString()
        );
        assertThat(transitions)
                .extracting(DocumentProcessingHistoryEntity::getToState)
                .containsExactly("RECEIVED", "PENDING_STORE", "STORED");

        var credential = certificateRepository.findById(certificateId).orElseThrow();
        assertThat(credential.getUsageCount()).isEqualTo(2);
        assertThat(credential.getLastUsedAt()).isNotNull();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
