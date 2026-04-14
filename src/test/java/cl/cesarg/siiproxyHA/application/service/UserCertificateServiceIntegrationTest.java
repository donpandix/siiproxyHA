package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class UserCertificateServiceIntegrationTest {

    @Container
    public static GenericContainer<?> minio = new GenericContainer("quay.io/minio/minio:latest")
            .withExposedPorts(9000, 9001)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data", "--console-address", ":9001");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String endpoint = String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000));
        registry.add("minio.endpoint", () -> endpoint);
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket", () -> "siiproxy-certificates");
        // 32 bytes as hex for AES (matches AesGcmCryptoService)
        byte[] rawKey = new byte[32];
        new SecureRandom().nextBytes(rawKey);
        registry.add("security.encryption.master-key", () -> HexFormat.of().formatHex(rawKey));
    }

    @Autowired
    private UserCertificateService service;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserCertificateRepository userCertRepo;

    @Test
    void uploadAndStoreCertificate() throws Exception {
        // create bucket using Minio client
        String endpoint = String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000));
        MinioClient mc = MinioClient.builder()
                .endpoint(endpoint)
                .credentials("minioadmin", "minioadmin")
                .build();
        try {
            mc.makeBucket(MakeBucketArgs.builder().bucket("siiproxy-certificates").build());
        } catch (Exception ignored) {}

        // create tenant
        Tenant t = new Tenant();
        UUID tid = UUID.randomUUID();
        t.setId(tid);
        t.setTenantCode("TCODE-" + tid.toString().substring(0, 8));
        t.setRutEmisor("12345678-9");
        t.setRazonSocial("Test Tenant");
        t.setCreatedAt(Instant.now());
        tenantRepository.save(t);

        // generate a self-signed cert (simple)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = SelfSignedCertGenerator.generate("CN=Test, SERIALNUMBER=12.345.678-9", kp);
        byte[] der = cert.getEncoded();

        var entity = service.uploadCertificate(tid, "12345678-9", "Test User", "tester", "test.crt",
                new ByteArrayInputStream(der), der.length, "application/x-x509-ca-cert", null, true);

        assertThat(entity).isNotNull();
        assertThat(entity.getCertSubject()).contains("CN=Test");
        assertThat(entity.getCertificatePath()).contains(tid.toString());
        assertThat(userCertRepo.findById(entity.getId())).isPresent();
    }
}
