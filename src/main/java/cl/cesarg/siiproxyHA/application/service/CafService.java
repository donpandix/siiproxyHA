package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.FolioPool;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.CafParserPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.FolioAssignmentRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.FolioPoolRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CafService {

    public static final String ASSIGNMENT_ASSIGNED = "ASSIGNED";
    public static final String ASSIGNMENT_USED = "USED";
    public static final String ASSIGNMENT_RELEASED = "RELEASED";
    public static final String POOL_ACTIVE = "ACTIVE";
    public static final String POOL_EXHAUSTED = "EXHAUSTED";

    private final CafRepository cafRepository;
    private final TenantRepository tenantRepository;
    private final FolioPoolRepository folioPoolRepository;
    private final FolioAssignmentRepository folioAssignmentRepository;
    private final DteRepository dteRepository;
    private final StoragePort storagePort;
    private final CafParserPort cafParser;

    public CafService(CafRepository cafRepository,
                      TenantRepository tenantRepository,
                      FolioPoolRepository folioPoolRepository,
                      FolioAssignmentRepository folioAssignmentRepository,
                      DteRepository dteRepository,
                      StoragePort storagePort,
                      CafParserPort cafParser) {
        this.cafRepository = cafRepository;
        this.tenantRepository = tenantRepository;
        this.folioPoolRepository = folioPoolRepository;
        this.folioAssignmentRepository = folioAssignmentRepository;
        this.dteRepository = dteRepository;
        this.storagePort = storagePort;
        this.cafParser = cafParser;
    }

    @Transactional
    public Caf create(UUID tenantId, byte[] xmlBytes, String originalFileName) throws Exception {
        return create(tenantId, 1, xmlBytes, originalFileName);
    }

    @Transactional
    public Caf create(UUID tenantId, Integer puntoVenta, byte[] xmlBytes, String originalFileName) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        CafParserPort.ParsedCaf parsed = cafParser.parse(xmlBytes);
        String tenantRut = RutUtils.normalizeAndValidate(tenant.getRutEmisor(), "tenant.rutEmisor");
        if (!tenantRut.equals(parsed.rutEmisor())) {
            throw new IllegalArgumentException("CAF issuer RUT does not match tenant");
        }

        // compute sha256 hex
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String sha256 = HexFormat.of().formatHex(md.digest(xmlBytes));

        // store file
        UUID id = UUID.randomUUID();
        String key = String.format(
                "caf/%s/%s-%s",
                tenantId,
                id,
                sanitizeFilename(originalFileName)
        );
        try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
            storagePort.store(key, in, xmlBytes.length, "application/xml");
        }

        Caf caf = new Caf();
        caf.setId(id);
        caf.setTenant(tenant);
        caf.setTipoDte(parsed.tipoDte());
        caf.setPuntoVenta(normalizePuntoVenta(puntoVenta));
        caf.setFolioDesde(parsed.folioDesde());
        caf.setFolioHasta(parsed.folioHasta());
        caf.setCafPath(key);
        caf.setCafSha256(sha256);
        caf.setRutEmisor(parsed.rutEmisor());
        caf.setFchAutorizacion(parsed.authorizationDate());
        caf.setCreatedAt(Instant.now());
        caf.setActive(true);

        Caf saved = cafRepository.save(caf);
        createPoolForCaf(saved);
        return saved;
    }

    public List<Caf> list() { return cafRepository.findAll(); }

    public Optional<Caf> get(UUID id) { return cafRepository.findById(id); }

    @Transactional
    public void delete(UUID id) { cafRepository.deleteById(id); }

    public byte[] downloadFile(Caf caf) throws Exception {
        byte[] authorizationXml = storagePort.get(caf.getCafPath());
        if (authorizationXml == null || authorizationXml.length == 0) {
            throw new IllegalStateException("Stored CAF is empty");
        }
        try {
            return cafParser.parse(authorizationXml).publicCafXml();
        } finally {
            Arrays.fill(authorizationXml, (byte) 0);
        }
    }

    @Transactional
    public FolioAssignment allocateNextFolio(UUID tenantId,
                                             Integer tipoDte,
                                             Integer puntoVenta,
                                             String requestId,
                                             String assignedTo) {
        int normalizedPuntoVenta = normalizePuntoVenta(puntoVenta);
        validateAllocationArgs(tenantId, tipoDte);

        if (requestId != null && !requestId.isBlank()) {
            Optional<FolioAssignment> existing = folioAssignmentRepository.findByTenantIdAndRequestId(tenantId, requestId.trim());
            if (existing.isPresent()) {
                FolioAssignment assignment = existing.get();
                if (!assignment.getTipoDte().equals(tipoDte) || !assignment.getPuntoVenta().equals(normalizedPuntoVenta)) {
                    throw new IllegalArgumentException("requestId already used for another tipoDte/puntoVenta");
                }
                return assignment;
            }
        }

        for (int attempt = 0; attempt < 20; attempt++) {
            Optional<FolioPool> poolOpt = folioPoolRepository.lockFirstActivePool(tenantId, tipoDte, normalizedPuntoVenta);
            if (poolOpt.isEmpty()) {
                throw new IllegalStateException("No active folio pool available for tenant/tipoDte/puntoVenta");
            }

            FolioPool pool = poolOpt.get();
            if (pool.getNextFolio() > pool.getFolioHasta()) {
                pool.setStatus(POOL_EXHAUSTED);
                pool.setUpdatedAt(Instant.now());
                folioPoolRepository.save(pool);
                continue;
            }

            long folio = pool.getNextFolio();
            pool.setNextFolio(folio + 1);
            if (pool.getNextFolio() > pool.getFolioHasta()) {
                pool.setStatus(POOL_EXHAUSTED);
            }
            pool.setUpdatedAt(Instant.now());
            folioPoolRepository.save(pool);

            FolioAssignment assignment = new FolioAssignment();
            assignment.setId(UUID.randomUUID());
            assignment.setTenant(pool.getTenant());
            assignment.setTipoDte(tipoDte);
            assignment.setPuntoVenta(normalizedPuntoVenta);
            assignment.setFolio(folio);
            assignment.setFolioPool(pool);
            assignment.setRequestId(requestId != null && !requestId.isBlank() ? requestId.trim() : null);
            assignment.setAssignedTo(assignedTo != null && !assignedTo.isBlank() ? assignedTo.trim() : "SYSTEM");
            assignment.setAssignedAt(Instant.now());
            assignment.setStatus(ASSIGNMENT_ASSIGNED);
            assignment.setNote("Allocated from CAF " + pool.getCaf().getId());
            return folioAssignmentRepository.save(assignment);
        }

        throw new IllegalStateException("Unable to allocate folio after multiple attempts");
    }

    @Transactional
    public FolioAssignment assignFolioToDte(UUID tenantId,
                                            UUID dteId,
                                            Integer puntoVenta,
                                            String requestId,
                                            String assignedTo) {
        Dte dte = dteRepository.findByIdAndTenantId(dteId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("DTE not found for tenant"));

        if (dte.getFolioAssignment() != null) {
            return dte.getFolioAssignment();
        }

        FolioAssignment assignment = allocateNextFolio(tenantId, dte.getTipoDte(), puntoVenta, requestId, assignedTo);
        assignment.setStatus(ASSIGNMENT_USED);
        assignment.setDte(dte);
        assignment.setNote("Assigned to DTE " + dte.getId());
        FolioAssignment savedAssignment = folioAssignmentRepository.save(assignment);

        dte.setFolio(savedAssignment.getFolio());
        dte.setFolioAssignment(savedAssignment);
        dte.setUpdatedAt(Instant.now());
        dteRepository.save(dte);

        return savedAssignment;
    }

    @Transactional
    public FolioAssignment releaseFolio(UUID tenantId, UUID assignmentId, String note) {
        FolioAssignment assignment = folioAssignmentRepository.findByIdAndTenantId(assignmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Folio assignment not found for tenant"));

        if (ASSIGNMENT_USED.equals(assignment.getStatus())) {
            throw new IllegalStateException("Cannot release folio in USED status");
        }

        assignment.setStatus(ASSIGNMENT_RELEASED);
        assignment.setNote(note != null && !note.isBlank() ? note.trim() : "Released manually");
        return folioAssignmentRepository.save(assignment);
    }

    public FolioStatus getFolioStatus(UUID tenantId, Integer tipoDte, Integer puntoVenta) {
        int normalizedPuntoVenta = normalizePuntoVenta(puntoVenta);
        List<FolioPool> pools = folioPoolRepository.findByTenantIdAndTipoDteAndPuntoVentaOrderByFolioDesdeAsc(
                tenantId,
                tipoDte,
                normalizedPuntoVenta
        );

        long available = 0L;
        Long next = null;
        for (FolioPool pool : pools) {
            long remaining = Math.max(0L, pool.getFolioHasta() - pool.getNextFolio() + 1);
            available += remaining;
            if (next == null && POOL_ACTIVE.equals(pool.getStatus()) && pool.getNextFolio() <= pool.getFolioHasta()) {
                next = pool.getNextFolio();
            }
        }

        return new FolioStatus(tenantId, tipoDte, normalizedPuntoVenta, next, available, pools.size());
    }

    private void createPoolForCaf(Caf caf) {
        FolioPool pool = new FolioPool();
        pool.setId(UUID.randomUUID());
        pool.setTenant(caf.getTenant());
        pool.setTipoDte(caf.getTipoDte());
        pool.setPuntoVenta(caf.getPuntoVenta());
        pool.setCaf(caf);
        pool.setFolioDesde(caf.getFolioDesde());
        pool.setFolioHasta(caf.getFolioHasta());
        pool.setNextFolio(caf.getFolioDesde());
        pool.setStatus(caf.getFolioDesde() <= caf.getFolioHasta() ? POOL_ACTIVE : POOL_EXHAUSTED);
        pool.setCreatedAt(Instant.now());
        pool.setUpdatedAt(Instant.now());
        folioPoolRepository.save(pool);
    }

    private int normalizePuntoVenta(Integer puntoVenta) {
        if (puntoVenta == null || puntoVenta < 1) {
            return 1;
        }
        return puntoVenta;
    }

    private String sanitizeFilename(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "caf.xml";
        }
        String normalized = originalFileName.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        String sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "caf.xml" : sanitized;
    }

    private void validateAllocationArgs(UUID tenantId, Integer tipoDte) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (tipoDte == null || tipoDte <= 0) {
            throw new IllegalArgumentException("tipoDte is required");
        }
    }

    public record FolioStatus(UUID tenantId,
                              Integer tipoDte,
                              Integer puntoVenta,
                              Long nextFolio,
                              Long availableFolios,
                              int poolCount) {
    }
}
