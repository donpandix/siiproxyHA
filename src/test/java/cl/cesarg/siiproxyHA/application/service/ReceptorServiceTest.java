package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.ReceptorDto;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.ReceptorRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceptorServiceTest {

    ReceptorRepository receptorRepository;
    TenantRepository tenantRepository;
    ReceptorService receptorService;

    @BeforeEach
    void setup() {
        receptorRepository = mock(ReceptorRepository.class);
        tenantRepository = mock(TenantRepository.class);
        receptorService = new ReceptorService(receptorRepository, tenantRepository);
    }

    @Test
    void create_succeeds_when_uniqueRut() {
        UUID tenantId = UUID.randomUUID();
        Tenant t = new Tenant();
        t.setId(tenantId);

        ReceptorDto dto = new ReceptorDto();
        dto.setRutReceptor("11111111-1");
        dto.setRazonSocial("Razon");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
        when(receptorRepository.existsByTenantIdAndRutReceptor(tenantId, "11111111-1")).thenReturn(false);
        when(receptorRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Receptor created = receptorService.create(tenantId, dto);

        assertNotNull(created.getId());
        assertEquals("11111111-1", created.getRutReceptor());
        verify(receptorRepository).save(any());
    }

    @Test
    void create_fails_when_duplicateRut() {
        UUID tenantId = UUID.randomUUID();
        Tenant t = new Tenant();
        t.setId(tenantId);

        ReceptorDto dto = new ReceptorDto();
        dto.setRutReceptor("22222222-2");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(t));
        when(receptorRepository.existsByTenantIdAndRutReceptor(tenantId, "22222222-2")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> receptorService.create(tenantId, dto));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(receptorRepository, never()).save(any());
    }
}
