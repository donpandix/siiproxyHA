package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteIngestPayload;
import cl.cesarg.siiproxyHA.application.dto.ReceptorDto;
import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DteItem;
import cl.cesarg.siiproxyHA.domain.model.DteReference;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.Receptor;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DteIngestService {

    private final TenantRepository tenantRepository;
    private final UserCertificateService userCertificateService;
    private final ReceptorService receptorService;
    private final DteCrudService dteCrudService;
    private final CafService cafService;
    private final DteService dteService;

    public DteIngestService(TenantRepository tenantRepository,
                            UserCertificateService userCertificateService,
                            ReceptorService receptorService,
                            DteCrudService dteCrudService,
                            CafService cafService,
                            DteService dteService) {
        this.tenantRepository = tenantRepository;
        this.userCertificateService = userCertificateService;
        this.receptorService = receptorService;
        this.dteCrudService = dteCrudService;
        this.cafService = cafService;
        this.dteService = dteService;
    }

    public DocumentMetadata ingest(DteIngestPayload payload) throws Exception {
        UUID tenantId = parseUuid(payload.tenantId, "tenantId");
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("tenant not found"));

        if (payload.tenantCode != null && !payload.tenantCode.equals(tenant.getTenantCode())) {
            throw new IllegalArgumentException("tenantCode does not match tenantId");
        }
        if (tenant.getFchResol() == null || tenant.getNroResol() == null) {
            throw new IllegalArgumentException("tenant SII resolution data is required");
        }

        String rutEnvia = RutUtils.normalizeAndValidate(payload.rutEnvia, "rutEnvia");
        userCertificateService.requireActiveCertificate(tenantId, rutEnvia);

        if (payload.id != null && !payload.id.isBlank()) {
            UUID requestedId = parseUuid(payload.id, "id");
            var existing = dteCrudService.findForStorage(requestedId, tenantId);
            if (existing.isPresent()) {
                return dteService.store(existing.get());
            }
        }

        Receptor receptor = receptorService.upsert(tenantId, toReceptorDto(payload.receptor));
        Instant now = Instant.now();

        Dte dte = new Dte();
        dte.setId(payload.id == null || payload.id.isBlank()
                ? UUID.randomUUID()
                : parseUuid(payload.id, "id"));
        dte.setTenant(tenant);
        dte.setRutEnvia(rutEnvia);
        dte.setTipoDte(payload.tipoDte == null ? 33 : payload.tipoDte);
        dte.setFolio(payload.folio == null ? 0L : payload.folio);
        dte.setFchEmis(parseDate(payload.fchEmis, "fchEmis"));
        dte.setReceptor(receptor);
        dte.setRutRecep(receptor.getRutReceptor());
        dte.setRznSocRecep(receptor.getRazonSocial());
        dte.setGiroRecep(receptor.getGiro());
        dte.setTelefonoRecep(receptor.getTelefono());
        dte.setDirRecep(receptor.getDireccion());
        dte.setCmnaRecep(receptor.getComuna());
        dte.setCiudadRecep(receptor.getCiudad());
        dte.setCorreoRecep(receptor.getEmail());
        dte.setMntNeto(payload.mntNeto);
        dte.setIva(payload.iva);
        dte.setMntTotal(payload.mntTotal);
        dte.setCreatedAt(now);
        dte.setUpdatedAt(now);

        dte.setItems(mapItems(payload.items, dte, now));
        dte.setReferences(mapReferences(payload.references, dte, now));

        Dte saved = dteCrudService.create(dte);
        if (saved.getFolio() == null || saved.getFolio() == 0L) {
            String requestId = payload.id == null || payload.id.isBlank()
                    ? saved.getId().toString()
                    : payload.id;
            FolioAssignment assignment =
                    cafService.assignFolioToDte(tenantId, saved.getId(), 1, requestId, "API");
            saved.setFolio(assignment.getFolio());
            saved.setFolioAssignment(assignment);
        }

        return dteService.store(saved);
    }

    private List<DteItem> mapItems(List<DteIngestPayload.Item> payloadItems,
                                   Dte dte,
                                   Instant now) {
        if (payloadItems == null || payloadItems.isEmpty()) return new ArrayList<>();

        List<DteItem> items = new ArrayList<>();
        for (DteIngestPayload.Item payloadItem : payloadItems) {
            DteItem item = new DteItem();
            item.setId(UUID.randomUUID());
            item.setNroLinDet(payloadItem.nroLinDet);
            item.setNmbItem(payloadItem.nmbItem);
            item.setDscItem(payloadItem.dscItem);
            item.setQtyItem(payloadItem.qtyItem);
            item.setUnmdItem(payloadItem.unmdItem);
            item.setPrcItem(payloadItem.prcItem);
            item.setMontoItem(payloadItem.montoItem);
            if (payloadItem.indExe != null && !payloadItem.indExe.isBlank()) {
                try {
                    item.setIndExe(Integer.valueOf(payloadItem.indExe));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("indExe is invalid");
                }
            }
            item.setCreatedAt(now);
            item.setDte(dte);
            items.add(item);
        }
        return items;
    }

    private List<DteReference> mapReferences(List<DteIngestPayload.Reference> payloadReferences,
                                             Dte dte,
                                             Instant now) {
        if (payloadReferences == null || payloadReferences.isEmpty()) return new ArrayList<>();

        List<DteReference> references = new ArrayList<>();
        for (DteIngestPayload.Reference payloadReference : payloadReferences) {
            DteReference reference = new DteReference();
            reference.setId(UUID.randomUUID());
            reference.setNroLinRef(payloadReference.nroLinRef);
            reference.setTpoDocRef(payloadReference.tpoDocRef);
            reference.setFolioRef(payloadReference.folioRef);
            if (payloadReference.fchRef != null && !payloadReference.fchRef.isBlank()) {
                reference.setFchRef(parseDate(payloadReference.fchRef, "fchRef"));
            }
            reference.setCodRef(payloadReference.codRef);
            reference.setRazonRef(payloadReference.razonRef);
            reference.setCreatedAt(now);
            reference.setDte(dte);
            references.add(reference);
        }
        return references;
    }

    private ReceptorDto toReceptorDto(DteIngestPayload.Receptor payload) {
        ReceptorDto dto = new ReceptorDto();
        dto.setRutReceptor(payload.rutReceptor);
        dto.setRazonSocial(payload.razonSocial);
        dto.setGiro(payload.giro);
        dto.setEmail(payload.email);
        dto.setTelefono(payload.telefono);
        dto.setDireccion(payload.direccion);
        dto.setComuna(payload.comuna);
        dto.setCiudad(payload.ciudad);
        return dto;
    }

    private UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
    }
}
