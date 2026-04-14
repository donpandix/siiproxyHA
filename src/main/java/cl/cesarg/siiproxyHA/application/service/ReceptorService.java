package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.ReceptorDto;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.ReceptorRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReceptorService {

    private final ReceptorRepository receptorRepository;
    private final TenantRepository tenantRepository;

    public ReceptorService(ReceptorRepository receptorRepository, TenantRepository tenantRepository) {
        this.receptorRepository = receptorRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public Receptor create(UUID tenantId, ReceptorDto dto) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        // validate unique rut per tenant when provided
        if (dto.getRutReceptor() != null && !dto.getRutReceptor().isBlank()) {
            if (receptorRepository.existsByTenantIdAndRutReceptor(tenantId, dto.getRutReceptor())) {
                throw new IllegalArgumentException("Receptor with same RUT already exists for tenant");
            }
        }

        Receptor r = new Receptor();
        r.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID());
        r.setTenant(tenant);
        r.setRutReceptor(dto.getRutReceptor());
        r.setRazonSocial(dto.getRazonSocial());
        r.setGiro(dto.getGiro());
        r.setEmail(dto.getEmail());
        r.setTelefono(dto.getTelefono());
        r.setDireccion(dto.getDireccion());
        r.setComuna(dto.getComuna());
        r.setCiudad(dto.getCiudad());
        r.setCreatedAt(Instant.now());
        return receptorRepository.save(r);
    }

    public List<Receptor> listByTenant(UUID tenantId) {
        return receptorRepository.findByTenantId(tenantId);
    }

    public Optional<Receptor> get(UUID id) { return receptorRepository.findById(id); }

    @Transactional
    public Optional<Receptor> update(UUID id, ReceptorDto dto) {
        return receptorRepository.findById(id).map(existing -> {
            // if updating rut, ensure uniqueness within tenant
            if (dto.getRutReceptor() != null && !dto.getRutReceptor().isBlank()
                    && !dto.getRutReceptor().equals(existing.getRutReceptor())) {
                UUID tenantId = existing.getTenant().getId();
                if (receptorRepository.existsByTenantIdAndRutReceptor(tenantId, dto.getRutReceptor())) {
                    throw new IllegalArgumentException("Receptor with same RUT already exists for tenant");
                }
            }
            existing.setRutReceptor(dto.getRutReceptor());
            existing.setRazonSocial(dto.getRazonSocial());
            existing.setGiro(dto.getGiro());
            existing.setEmail(dto.getEmail());
            existing.setTelefono(dto.getTelefono());
            existing.setDireccion(dto.getDireccion());
            existing.setComuna(dto.getComuna());
            existing.setCiudad(dto.getCiudad());
            return receptorRepository.save(existing);
        });
    }

    @Transactional
    public void delete(UUID id) { receptorRepository.deleteById(id); }
}
