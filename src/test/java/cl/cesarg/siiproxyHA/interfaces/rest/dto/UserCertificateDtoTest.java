package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserCertificateDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializedResponseDoesNotExposeSensitiveStorageOrEncryptionFields() throws Exception {
        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setRutUsuario("10438332-7");
        entity.setCertificatePath("tenants/private/certificate.pfx");
        entity.setCertificateHash("secret-hash");
        entity.setEncryptedPassword("encrypted-secret");
        entity.setEncryptionIv("secret-iv");
        entity.setEncryptionAlgorithm("AES/GCM/NoPadding");

        String json = objectMapper.writeValueAsString(UserCertificateDto.fromEntity(entity));

        assertThat(json).contains("10438332-7");
        assertThat(json)
                .doesNotContain("certificatePath")
                .doesNotContain("certificateHash")
                .doesNotContain("encryptedPassword")
                .doesNotContain("encryptionIv")
                .doesNotContain("encryptionAlgorithm")
                .doesNotContain("encrypted-secret")
                .doesNotContain("secret-iv");
    }
}
