package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DocumentoRepositoryAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DocumentoRepositoryPostgresIntegrationTest {

    @Container
    static final GenericContainer<?> postgres = new GenericContainer<>("postgres:17")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "metadata_test")
            .withEnv("POSTGRES_USER", "metadata_test")
            .withEnv("POSTGRES_PASSWORD", "metadata_test")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
                "jdbc:postgresql://%s:%d/metadata_test", postgres.getHost(), postgres.getMappedPort(5432)));
        registry.add("spring.datasource.username", () -> "metadata_test");
        registry.add("spring.datasource.password", () -> "metadata_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private DocumentoRepositoryAdapter repository;

    @Autowired
    private DocumentProcessingHistoryRepository historyRepository;

    @Test
    void createsClaimsAndUpdatesSameMetadataRow() {
        String documentId = UUID.randomUUID().toString();
        DocumentMetadata created = new DocumentMetadata(documentId, DocumentStatus.RECEIVED);
        created.setObjectKey("dte/2026/07/" + documentId + "-182.xml");
        created.setFolio("182");

        DocumentMetadata first = repository.createIfAbsent(created);
        DocumentMetadata duplicate = repository.createIfAbsent(
                new DocumentMetadata(documentId, DocumentStatus.RECEIVED));

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(repository.tryClaimStore(documentId, OffsetDateTime.now().minusMinutes(5))).isTrue();

        DocumentMetadata claimed = repository.findByDocumentId(documentId).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(DocumentStatus.PENDING_STORE);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);

        claimed.setStatus(DocumentStatus.STORED);
        claimed.setSha256("a".repeat(64));
        claimed.setSizeBytes(123L);
        repository.save(claimed);

        DocumentMetadata stored = repository.findByDocumentId(documentId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(first.getId());
        assertThat(stored.getStatus()).isEqualTo(DocumentStatus.STORED);
        assertThat(stored.getSha256()).isEqualTo("a".repeat(64));
        assertThat(stored.getSizeBytes()).isEqualTo(123L);
        assertThat(repository.tryClaimStore(documentId, OffsetDateTime.now())).isFalse();

        var transitions = historyRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        assertThat(transitions).extracting(DocumentProcessingHistoryEntity::getToState)
                .containsExactly("RECEIVED", "PENDING_STORE", "STORED");
    }
}
