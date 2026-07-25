package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DteItem;
import cl.cesarg.siiproxyHA.domain.model.DteReference;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DteCrudService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DteCrudServicePostgresIntegrationTest {

    @Container
    static final GenericContainer<?> postgres = new GenericContainer<>("postgres:17")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "dte_test")
            .withEnv("POSTGRES_USER", "dte_test")
            .withEnv("POSTGRES_PASSWORD", "dte_test")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
                "jdbc:postgresql://%s:%d/dte_test", postgres.getHost(), postgres.getMappedPort(5432)));
        registry.add("spring.datasource.username", () -> "dte_test");
        registry.add("spring.datasource.password", () -> "dte_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private DteCrudService service;

    @Autowired
    private DteRepository dteRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void persistsTwoItemsAndOneReferenceWithOneCascadeSave() {
        Instant now = Instant.now();
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setTenantCode("TEST-" + tenant.getId().toString().substring(0, 8));
        tenant.setRutEmisor("76184688-4");
        tenant.setRazonSocial("Test Tenant");
        tenant.setCreatedAt(now);
        tenant.setActive(true);
        tenantRepository.saveAndFlush(tenant);

        Dte dte = new Dte();
        dte.setId(UUID.randomUUID());
        dte.setTenant(tenant);
        dte.setTipoDte(33);
        dte.setFolio(182L);
        dte.setFchEmis(LocalDate.of(2026, 7, 22));
        dte.setRznSocRecep("Cliente Uno");
        dte.setMntNeto(7000L);
        dte.setIva(1330L);
        dte.setMntTotal(8330L);
        dte.setCreatedAt(now);
        dte.setUpdatedAt(now);

        DteItem firstItem = item(1, "Producto A", 2000L, now);
        DteItem secondItem = item(2, "Producto B", 5000L, now);
        dte.setItems(List.of(firstItem, secondItem));

        DteReference reference = new DteReference();
        reference.setId(UUID.randomUUID());
        reference.setNroLinRef(1);
        reference.setTpoDocRef("SET");
        reference.setFolioRef("182");
        reference.setFchRef(LocalDate.of(2026, 7, 22));
        reference.setCodRef("1");
        reference.setRazonRef("referencias de prueba");
        reference.setCreatedAt(now);
        dte.setReferences(List.of(reference));

        service.create(dte);

        Dte stored = dteRepository.findById(dte.getId()).orElseThrow();
        assertThat(stored.getItems()).hasSize(2);
        assertThat(stored.getReferences()).hasSize(1);
    }

    private DteItem item(int line, String name, long amount, Instant now) {
        DteItem item = new DteItem();
        item.setId(UUID.randomUUID());
        item.setNroLinDet(line);
        item.setNmbItem(name);
        item.setMontoItem(amount);
        item.setCreatedAt(now);
        return item;
    }
}
