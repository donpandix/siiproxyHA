package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.ReceptorDto;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
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
            dto.setRutReceptor(RutUtils.normalizeAndValidate(dto.getRutReceptor(), "rutReceptor"));
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

    @Transactional
    public Receptor upsert(UUID tenantId, ReceptorDto dto) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        String rutReceptor = RutUtils.normalizeAndValidate(dto.getRutReceptor(), "rutReceptor");

        Optional<Receptor> exactMatch = receptorRepository.findByTenantIdAndRutReceptor(tenantId, rutReceptor);
        Optional<Receptor> normalizedLegacyMatch = exactMatch.isPresent()
                ? exactMatch
                : receptorRepository.findByTenantId(tenantId).stream()
                        .filter(existing -> rutReceptor.equals(RutUtils.normalize(existing.getRutReceptor())))
                        .findFirst();

        Receptor receptor = normalizedLegacyMatch
                .orElseGet(() -> {
                    Receptor created = new Receptor();
                    created.setId(UUID.randomUUID());
                    created.setTenant(tenant);
                    created.setRutReceptor(rutReceptor);
                    created.setCreatedAt(Instant.now());
                    return created;
                });

        receptor.setRutReceptor(rutReceptor);
        receptor.setRazonSocial(dto.getRazonSocial());
        receptor.setGiro(dto.getGiro());
        receptor.setEmail(dto.getEmail());
        receptor.setTelefono(dto.getTelefono());
        receptor.setDireccion(dto.getDireccion());
        receptor.setComuna(dto.getComuna());
        receptor.setCiudad(dto.getCiudad());
        return receptorRepository.save(receptor);
    }

    public List<Receptor> listByTenant(UUID tenantId) {
        return receptorRepository.findByTenantId(tenantId);
    }

    public Optional<Receptor> get(UUID id) { return receptorRepository.findById(id); }

    @Transactional
    public Optional<Receptor> update(UUID id, ReceptorDto dto) {
        return receptorRepository.findById(id).map(existing -> {
            // if updating rut, ensure uniqueness within tenant
            if (dto.getRutReceptor() != null && !dto.getRutReceptor().isBlank()) {
                dto.setRutReceptor(RutUtils.normalizeAndValidate(dto.getRutReceptor(), "rutReceptor"));
            }
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
