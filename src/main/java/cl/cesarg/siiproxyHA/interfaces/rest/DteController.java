package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.application.dto.DteIngestPayload;
import cl.cesarg.siiproxyHA.application.service.DteCrudService;
import cl.cesarg.siiproxyHA.application.service.ReceptorService;
import cl.cesarg.siiproxyHA.domain.model.DteItem;
import cl.cesarg.siiproxyHA.domain.model.DteReference;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.application.service.CafService;
import cl.cesarg.siiproxyHA.application.service.DteService;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/dte")
public class DteController {

    private final DteService dteService;
    private final DteCrudService dteCrudService;
    private final ReceptorService receptorService;
    private final TenantRepository tenantRepository;
    private final CafService cafService;
    private final cl.cesarg.siiproxyHA.application.service.ProductService productService;

    public DteController(DteService dteService,
                         DteCrudService dteCrudService,
                         ReceptorService receptorService,
                         TenantRepository tenantRepository,
                         CafService cafService,
                         cl.cesarg.siiproxyHA.application.service.ProductService productService) {
        this.dteService = dteService;
        this.dteCrudService = dteCrudService;
        this.receptorService = receptorService;
        this.tenantRepository = tenantRepository;
        this.cafService = cafService;
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<DocumentMetadata> ingest(@Valid @RequestBody DteIngestPayload payload) throws Exception {
        // Validate tenant
        if (payload.tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        Tenant tenant = tenantRepository.findById(java.util.UUID.fromString(payload.tenantId))
                .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        if (payload.tenantCode != null && !payload.tenantCode.equals(tenant.getTenantCode())) {
            throw new IllegalArgumentException("tenantCode does not match tenantId");
        }

        // Validate receptor belongs to tenant
        if (payload.receptorId != null) {
            var receptorOpt = receptorService.get(java.util.UUID.fromString(payload.receptorId));
            if (receptorOpt.isEmpty()) {
                throw new IllegalArgumentException("receptor not found");
            }
            var receptor = receptorOpt.get();
            if (receptor.getTenant() == null || !receptor.getTenant().getId().equals(tenant.getId())) {
                throw new IllegalArgumentException("receptor does not belong to tenant");
            }
        }

        // Map payload -> domain Dte
        Dte dte = new Dte();
        dte.setId(payload.id != null ? java.util.UUID.fromString(payload.id) : java.util.UUID.randomUUID());
        dte.setTenant(tenant);
        dte.setTipoDte(payload.tipoDte == null ? 33 : payload.tipoDte);
        dte.setFolio(payload.folio == null ? 0L : payload.folio);
        if (payload.fchEmis != null) {
            dte.setFchEmis(java.time.LocalDate.parse(payload.fchEmis));
        }
        if (payload.receptorId != null) {
            var receptor = receptorService.get(java.util.UUID.fromString(payload.receptorId)).get();
            dte.setReceptor(receptor);
            dte.setRutRecep(receptor.getRutReceptor());
            dte.setRznSocRecep(receptor.getRazonSocial());
            dte.setDirRecep(receptor.getDireccion());
            dte.setCmnaRecep(receptor.getComuna());
        }
        dte.setMntNeto(payload.mntNeto);
        dte.setIva(payload.iva);
        dte.setMntTotal(payload.mntTotal);

        if (payload.items != null && !payload.items.isEmpty()) {
            java.util.List<DteItem> items = new java.util.ArrayList<>();
            // validate that item ids belong to tenant
            java.util.List<String> itemIds = new java.util.ArrayList<>();
            for (var it : payload.items) {
                if (it.id != null) itemIds.add(it.id);
                DteItem di = new DteItem();
                di.setId(it.id != null ? java.util.UUID.fromString(it.id) : java.util.UUID.randomUUID());
                di.setNroLinDet(it.nroLinDet);
                di.setNmbItem(it.nmbItem);
                di.setDscItem(it.dscItem);
                di.setQtyItem(it.qtyItem == null ? null : it.qtyItem);
                di.setUnmdItem(it.unmdItem);
                di.setPrcItem(it.prcItem == null ? null : it.prcItem);
                di.setMontoItem(it.montoItem == null ? null : it.montoItem);
                di.setDte(dte);
                items.add(di);
            }
            if (!itemIds.isEmpty()) {
                productService.validateItemsBelongToTenant(tenant.getId(), itemIds);
            }
            dte.setItems(items);
        }

        if (payload.references != null && !payload.references.isEmpty()) {
            java.util.List<DteReference> refs = new java.util.ArrayList<>();
            for (var r : payload.references) {
                DteReference ref = new DteReference();
                ref.setNroLinRef(r.nroLinRef);
                ref.setTpoDocRef(r.tpoDocRef);
                ref.setFolioRef(r.folioRef);
                if (r.fchRef != null) {
                    ref.setFchRef(java.time.LocalDate.parse(r.fchRef));
                }
                ref.setCodRef(r.codRef);
                ref.setRazonRef(r.razonRef);
                ref.setDte(dte);
                refs.add(ref);
            }
            dte.setReferences(refs);
        }

        // Persist full DTE via CRUD service
        Dte saved = dteCrudService.create(dte);

        // Assign folio if not provided (folio == 0)
        if (saved.getFolio() == null || saved.getFolio() == 0L) {
            // use request id or payload id as assignment request id
            String assignmentRequestId = payload.id != null ? payload.id : java.util.UUID.randomUUID().toString();
            cafService.assignFolioToDte(
                    tenant.getId(),
                    saved.getId(),
                    1,
                    assignmentRequestId,
                    "API"
            );
            // reload saved
            saved = dteCrudService.findById(saved.getId()).orElse(saved);
        }

        // Build DocumentMetadata
        String documentId = saved.getId().toString();
        cl.cesarg.siiproxyHA.domain.model.DocumentMetadata meta = new cl.cesarg.siiproxyHA.domain.model.DocumentMetadata(documentId, cl.cesarg.siiproxyHA.domain.model.DocumentStatus.RECEIVED);
        if (saved.getFolio() != null && saved.getFolio() > 0) {
            meta.setFolio(String.valueOf(saved.getFolio()));
        }
        meta = dteService.getStatus(documentId); // delegate to existing status behavior if present
        return ResponseEntity.status(201).body(meta);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DocumentMetadata> status(@PathVariable("id") String id) throws Exception {
        DocumentMetadata meta = dteService.getStatus(id);
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<?> xml(@PathVariable("id") String id,
                                 @RequestParam(name = "presigned", required = false, defaultValue = "false") boolean presigned,
                                 @RequestParam(name = "expiryMinutes", required = false, defaultValue = "60") int expiryMinutes,
                                 @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept
    ) throws Exception {
        var resp = dteService.getXml(id, presigned, expiryMinutes);

        if (accept != null
                && accept.contains(MediaType.APPLICATION_XML_VALUE)
                && resp.getXmlBase64() != null
                && !resp.getXmlBase64().isBlank()) {
            String xml = new String(Base64.getDecoder().decode(resp.getXmlBase64()), StandardCharsets.UTF_8);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
        }

        return ResponseEntity.ok(resp);
    }
}
