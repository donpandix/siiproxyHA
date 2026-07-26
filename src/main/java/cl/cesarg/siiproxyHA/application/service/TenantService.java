package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.ReceptorDto;
import cl.cesarg.siiproxyHA.application.dto.TenantDto;
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
import java.util.stream.Collectors;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ReceptorRepository receptorRepository;

    public TenantService(TenantRepository tenantRepository, ReceptorRepository receptorRepository) {
        this.tenantRepository = tenantRepository;
        this.receptorRepository = receptorRepository;
    }

    @Transactional
    public Tenant create(TenantDto dto) {
        Tenant t = new Tenant();
        UUID id = dto.getId() != null ? dto.getId() : UUID.randomUUID();
        t.setId(id);
        t.setTenantCode(dto.getTenantCode());
        t.setRutEmisor(dto.getRutEmisor());
        t.setRazonSocial(dto.getRazonSocial());
        t.setGiro(dto.getGiro());
        t.setActeco(dto.getActeco());
        t.setDireccion(dto.getDireccion());
        t.setComuna(dto.getComuna());
        t.setCiudad(dto.getCiudad());
        t.setEmail(dto.getEmail());
        t.setFchResol(dto.getFchResol());
        t.setNroResol(dto.getNroResol());
        t.setActive(dto.getActive() == null || dto.getActive());
        t.setCreatedAt(Instant.now());

        if (dto.getReceptores() != null) {
            List<Receptor> recs = dto.getReceptores().stream().map(rdto -> {
                Receptor r = new Receptor();
                r.setId(rdto.getId() != null ? rdto.getId() : UUID.randomUUID());
                r.setRazonSocial(rdto.getRazonSocial());
                r.setRutReceptor(rdto.getRutReceptor());
                r.setGiro(rdto.getGiro());
                r.setEmail(rdto.getEmail());
                r.setTelefono(rdto.getTelefono());
                r.setDireccion(rdto.getDireccion());
                r.setComuna(rdto.getComuna());
                r.setCiudad(rdto.getCiudad());
                r.setCreatedAt(Instant.now());
                r.setTenant(t);
                return r;
            }).collect(Collectors.toList());
            t.setReceptores(recs);
        }

        return tenantRepository.save(t);
    }

    public List<Tenant> list() { return tenantRepository.findAll(); }

    public Optional<Tenant> get(UUID id) { return tenantRepository.findById(id); }

    /**
     * Applies only fields present in the request, preserving omitted tenant data and receptors.
     */
    @Transactional
    public Optional<Tenant> update(UUID id, TenantDto dto) {
        return tenantRepository.findById(id).map(existing -> {
            if (dto.getTenantCode() != null) {
                existing.setTenantCode(dto.getTenantCode());
            }
            if (dto.getRutEmisor() != null) {
                existing.setRutEmisor(dto.getRutEmisor());
            }
            if (dto.getRazonSocial() != null) {
                existing.setRazonSocial(dto.getRazonSocial());
            }
            if (dto.getGiro() != null) {
                existing.setGiro(dto.getGiro());
            }
            if (dto.getActeco() != null) {
                existing.setActeco(dto.getActeco());
            }
            if (dto.getDireccion() != null) {
                existing.setDireccion(dto.getDireccion());
            }
            if (dto.getComuna() != null) {
                existing.setComuna(dto.getComuna());
            }
            if (dto.getCiudad() != null) {
                existing.setCiudad(dto.getCiudad());
            }
            if (dto.getEmail() != null) {
                existing.setEmail(dto.getEmail());
            }
            if (dto.getFchResol() != null) {
                existing.setFchResol(dto.getFchResol());
            }
            if (dto.getNroResol() != null) {
                existing.setNroResol(dto.getNroResol());
            }
            if (dto.getActive() != null) {
                existing.setActive(dto.getActive());
            }

            if (dto.getReceptores() != null) {
                existing.getReceptores().clear();
                List<Receptor> recs = dto.getReceptores().stream().map(rdto -> {
                    Receptor r = new Receptor();
                    r.setId(rdto.getId() != null ? rdto.getId() : UUID.randomUUID());
                    r.setRazonSocial(rdto.getRazonSocial());
                    r.setRutReceptor(rdto.getRutReceptor());
                    r.setGiro(rdto.getGiro());
                    r.setEmail(rdto.getEmail());
                    r.setTelefono(rdto.getTelefono());
                    r.setDireccion(rdto.getDireccion());
                    r.setComuna(rdto.getComuna());
                    r.setCiudad(rdto.getCiudad());
                    r.setCreatedAt(Instant.now());
                    r.setTenant(existing);
                    return r;
                }).collect(Collectors.toList());
                existing.getReceptores().addAll(recs);
            }

            return tenantRepository.save(existing);
        });
    }

    @Transactional
    public void delete(UUID id) { tenantRepository.deleteById(id); }
}
