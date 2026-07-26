package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.TenantDto;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import jakarta.persistence.EntityManager;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TenantService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TenantServicePostgresIntegrationTest {

    @Container
    static final GenericContainer<?> postgres = new GenericContainer<>("postgres:17")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "tenant_update_test")
            .withEnv("POSTGRES_USER", "tenant_update_test")
            .withEnv("POSTGRES_PASSWORD", "tenant_update_test")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
                "jdbc:postgresql://%s:%d/tenant_update_test",
                postgres.getHost(),
                postgres.getMappedPort(5432)
        ));
        registry.add("spring.datasource.username", () -> "tenant_update_test");
        registry.add("spring.datasource.password", () -> "tenant_update_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TenantService service;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsPartialUpdateWithoutNullingRequiredFieldsOrDeletingReceptors() {
        Tenant tenant = tenantWithReceptor();
        tenantRepository.saveAndFlush(tenant);
        UUID receptorId = tenant.getReceptores().getFirst().getId();

        TenantDto update = new TenantDto();
        update.setActeco("726000");
        update.setEmail("cesar@cesarg.cl");
        update.setFchResol(LocalDate.of(2020, 8, 14));
        update.setNroResol(80);
        update.setActive(true);

        service.update(tenant.getId(), update).orElseThrow();
        tenantRepository.flush();
        entityManager.clear();

        Tenant stored = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(stored.getTenantCode()).isEqualTo("TENANT-ORIGINAL");
        assertThat(stored.getRutEmisor()).isEqualTo("76184688-4");
        assertThat(stored.getRazonSocial()).isEqualTo("Empresa Original");
        assertThat(stored.getActeco()).isEqualTo("726000");
        assertThat(stored.getEmail()).isEqualTo("cesar@cesarg.cl");
        assertThat(stored.getFchResol()).isEqualTo(LocalDate.of(2020, 8, 14));
        assertThat(stored.getNroResol()).isEqualTo(80);
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.getReceptores())
                .extracting(Receptor::getId)
                .containsExactly(receptorId);
    }

    private Tenant tenantWithReceptor() {
        Instant now = Instant.now();
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setTenantCode("TENANT-ORIGINAL");
        tenant.setRutEmisor("76184688-4");
        tenant.setRazonSocial("Empresa Original");
        tenant.setCreatedAt(now);
        tenant.setActive(false);

        Receptor receptor = new Receptor();
        receptor.setId(UUID.randomUUID());
        receptor.setTenant(tenant);
        receptor.setRutReceptor("60803000-K");
        receptor.setRazonSocial("Servicio de Impuestos Internos");
        receptor.setCreatedAt(now);
        tenant.setReceptores(new ArrayList<>());
        tenant.getReceptores().add(receptor);
        return tenant;
    }
}
