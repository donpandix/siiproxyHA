package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DteItem;
import cl.cesarg.siiproxyHA.domain.model.DteReference;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteItemRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteReferenceRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DteCrudService {

    private final DteRepository dteRepository;
    private final DteItemRepository dteItemRepository;
    private final DteReferenceRepository dteReferenceRepository;

    public DteCrudService(DteRepository dteRepository,
                          DteItemRepository dteItemRepository,
                          DteReferenceRepository dteReferenceRepository) {
        this.dteRepository = dteRepository;
        this.dteItemRepository = dteItemRepository;
        this.dteReferenceRepository = dteReferenceRepository;
    }

    @Transactional
    public Dte create(Dte dte) {
        Dte saved = dteRepository.save(dte);
        if (dte.getItems() != null) {
            List<DteItem> items = dte.getItems().stream().map(i -> {
                i.setDte(saved);
                return i;
            }).collect(Collectors.toList());
            dteItemRepository.saveAll(items);
            saved.setItems(items);
        }
        if (dte.getReferences() != null) {
            List<DteReference> refs = dte.getReferences().stream().map(r -> {
                r.setDte(saved);
                return r;
            }).collect(Collectors.toList());
            dteReferenceRepository.saveAll(refs);
            saved.setReferences(refs);
        }
        return saved;
    }

    public Optional<Dte> findById(UUID id) {
        return dteRepository.findById(id);
    }

    public Optional<Dte> findByIdAndTenantId(UUID id, UUID tenantId) {
        if (tenantId == null) {
            return dteRepository.findById(id);
        }
        return dteRepository.findByIdAndTenantId(id, tenantId);
    }

    public List<Dte> findAllByTenantId(UUID tenantId) {
        if (tenantId == null) {
            return dteRepository.findAll();
        }
        return dteRepository.findByTenantId(tenantId);
    }

    @Transactional
    public Optional<Dte> update(UUID id, Dte updated) {
        return dteRepository.findById(id).map(existing -> {
            existing.setTipoDte(updated.getTipoDte());
            existing.setFolio(updated.getFolio());
            existing.setFchEmis(updated.getFchEmis());
            existing.setMntNeto(updated.getMntNeto());
            existing.setIva(updated.getIva());
            existing.setMntTotal(updated.getMntTotal());
            // replace items
            dteItemRepository.deleteAll(existing.getItems());
            if (updated.getItems() != null) {
                List<DteItem> items = updated.getItems().stream().map(i -> {
                    i.setDte(existing);
                    return i;
                }).collect(Collectors.toList());
                dteItemRepository.saveAll(items);
                existing.setItems(items);
            }
            // replace references
            dteReferenceRepository.deleteAll(existing.getReferences());
            if (updated.getReferences() != null) {
                List<DteReference> refs = updated.getReferences().stream().map(r -> {
                    r.setDte(existing);
                    return r;
                }).collect(Collectors.toList());
                dteReferenceRepository.saveAll(refs);
                existing.setReferences(refs);
            }
            return existing;
        });
    }

    @Transactional
    public Optional<Dte> updateForTenant(UUID id, UUID tenantId, Dte updated) {
        return findByIdAndTenantId(id, tenantId).map(existing -> {
            existing.setTipoDte(updated.getTipoDte());
            existing.setFolio(updated.getFolio());
            existing.setFchEmis(updated.getFchEmis());
            existing.setMntNeto(updated.getMntNeto());
            existing.setIva(updated.getIva());
            existing.setMntTotal(updated.getMntTotal());
            // replace items
            dteItemRepository.deleteAll(existing.getItems());
            if (updated.getItems() != null) {
                List<DteItem> items = updated.getItems().stream().map(i -> {
                    i.setDte(existing);
                    return i;
                }).collect(Collectors.toList());
                dteItemRepository.saveAll(items);
                existing.setItems(items);
            }
            // replace references
            dteReferenceRepository.deleteAll(existing.getReferences());
            if (updated.getReferences() != null) {
                List<DteReference> refs = updated.getReferences().stream().map(r -> {
                    r.setDte(existing);
                    return r;
                }).collect(Collectors.toList());
                dteReferenceRepository.saveAll(refs);
                existing.setReferences(refs);
            }
            return existing;
        });
    }

    @Transactional
    public void delete(UUID id) {
        dteRepository.findById(id).ifPresent(dte -> {
            dteItemRepository.deleteAll(dte.getItems());
            dteReferenceRepository.deleteAll(dte.getReferences());
            dteRepository.delete(dte);
        });
    }

    @Transactional
    public boolean deleteForTenant(UUID id, UUID tenantId) {
        Optional<Dte> found = findByIdAndTenantId(id, tenantId);
        if (found.isPresent()) {
            Dte dte = found.get();
            dteItemRepository.deleteAll(dte.getItems());
            dteReferenceRepository.deleteAll(dte.getReferences());
            dteRepository.delete(dte);
            return true;
        }
        return false;
    }
}
