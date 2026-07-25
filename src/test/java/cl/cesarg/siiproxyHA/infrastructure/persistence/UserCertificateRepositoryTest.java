package cl.cesarg.siiproxyHA.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserCertificateRepositoryTest {

    @Autowired
    private UserCertificateRepository repository;

    @Test
    void recordsSuccessfulUseAtomicallyForActiveCredential() {
        UserCertificateEntity entity = repository.saveAndFlush(certificate("ACTIVE"));
        OffsetDateTime usedAt = OffsetDateTime.now(ZoneOffset.UTC);

        int updated = repository.recordSuccessfulUse(entity.getId(), usedAt);
        UserCertificateEntity reloaded = repository.findById(entity.getId()).orElseThrow();

        assertEquals(1, updated);
        assertEquals(1, reloaded.getUsageCount());
        assertEquals(usedAt, reloaded.getLastUsedAt());
    }

    @Test
    void doesNotRecordUseForInactiveCredential() {
        UserCertificateEntity entity = repository.saveAndFlush(certificate("DISABLED"));

        int updated = repository.recordSuccessfulUse(
                entity.getId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        assertEquals(0, updated);
        assertEquals(0, repository.findById(entity.getId()).orElseThrow().getUsageCount());
    }

    private UserCertificateEntity certificate(String status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setRutUsuario("10438332-7");
        entity.setCertificatePath("tenants/test/certs/credential.p12");
        entity.setCertificateHash("a".repeat(64));
        entity.setEncryptedPassword("encrypted-password");
        entity.setEncryptionIv("iv");
        entity.setEncryptionAlgorithm("AES/GCM/NoPadding");
        entity.setCertSerialNumber("123");
        entity.setCertSubject("CN=Test,SERIALNUMBER=10.438.332-7");
        entity.setCertSubjectRut("10438332-7");
        entity.setValidFrom(now.minusDays(1));
        entity.setValidUntil(now.plusDays(1));
        entity.setStatus(status);
        entity.setDefault(true);
        entity.setCreatedAt(now);
        entity.setUsageCount(0);
        return entity;
    }
}
