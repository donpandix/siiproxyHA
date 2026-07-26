package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.TenantDto;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.ReceptorRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final ReceptorRepository receptorRepository = mock(ReceptorRepository.class);
    private final TenantService service = new TenantService(tenantRepository, receptorRepository);

    @Test
    void updatesOnlyProvidedFieldsAndPreservesIdentityAndReceptors() {
        UUID tenantId = UUID.randomUUID();
        Tenant existing = tenant(tenantId);
        Receptor receptor = new Receptor();
        receptor.setId(UUID.randomUUID());
        receptor.setTenant(existing);
        existing.setReceptores(new ArrayList<>());
        existing.getReceptores().add(receptor);

        TenantDto update = new TenantDto();
        update.setActeco("726000");
        update.setEmail("cesar@cesarg.cl");
        update.setFchResol(LocalDate.of(2020, 8, 14));
        update.setNroResol(80);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant updated = service.update(tenantId, update).orElseThrow();

        assertThat(updated.getTenantCode()).isEqualTo("TENANT-ORIGINAL");
        assertThat(updated.getRutEmisor()).isEqualTo("76184688-4");
        assertThat(updated.getRazonSocial()).isEqualTo("Empresa Original");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getReceptores()).containsExactly(receptor);
        assertThat(updated.getActeco()).isEqualTo("726000");
        assertThat(updated.getEmail()).isEqualTo("cesar@cesarg.cl");
        assertThat(updated.getFchResol()).isEqualTo(LocalDate.of(2020, 8, 14));
        assertThat(updated.getNroResol()).isEqualTo(80);
        verify(tenantRepository).save(existing);
        verify(receptorRepository, never()).delete(any(Receptor.class));
    }

    @Test
    void clearsReceptorsOnlyWhenAnEmptyListIsExplicitlyProvided() {
        UUID tenantId = UUID.randomUUID();
        Tenant existing = tenant(tenantId);
        Receptor receptor = new Receptor();
        receptor.setId(UUID.randomUUID());
        receptor.setTenant(existing);
        existing.setReceptores(new ArrayList<>());
        existing.getReceptores().add(receptor);

        TenantDto update = new TenantDto();
        update.setReceptores(new ArrayList<>());

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant updated = service.update(tenantId, update).orElseThrow();

        assertThat(updated.getReceptores()).isEmpty();
    }

    private Tenant tenant(UUID tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setTenantCode("TENANT-ORIGINAL");
        tenant.setRutEmisor("76184688-4");
        tenant.setRazonSocial("Empresa Original");
        tenant.setActeco("123456");
        tenant.setEmail("original@example.cl");
        tenant.setActive(false);
        return tenant;
    }
}
