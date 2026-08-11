package cl.cesarg.siiproxyHA.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.application.exception.IdempotencyKeyReusedException;
import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.application.exception.UnsupportedDocumentTypeException;
import cl.cesarg.siiproxyHA.domain.model.ApiIdempotencyEntity;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.infrastructure.persistence.ApiIdempotencyRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentRequest;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentResponse;
import cl.cesarg.siiproxyHA.interfaces.rest.dto.PublicDocumentSiiStatus;

@Service
public class PublicDocumentService {

    public record CreateDocumentResult(PublicDocumentResponse response, boolean replay) {
    }

    private static final String OPERATION_CREATE_DOCUMENT = "CREATE_DOCUMENT";

    private final TenantRepository tenantRepository;
    private final ApiIdempotencyRepository apiIdempotencyRepository;
    private final DteRepository dteRepository;
    private final DteService dteService;
    private final ObjectMapper objectMapper;

    public PublicDocumentService(TenantRepository tenantRepository,
                                ApiIdempotencyRepository apiIdempotencyRepository,
                                DteRepository dteRepository,
                                DteService dteService,
                                ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.apiIdempotencyRepository = apiIdempotencyRepository;
        this.dteRepository = dteRepository;
        this.dteService = dteService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateDocumentResult createDocument(PublicDocumentRequest request, UUID tenantId, String idempotencyKey) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TENANT_NOT_FOUND"));

        if (request == null || request.getType() == null || !"INVOICE".equalsIgnoreCase(request.getType())) {
            throw new UnsupportedDocumentTypeException("DOCUMENT_TYPE_NOT_SUPPORTED");
        }

        String requestHash = hashRequest(request);
        String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();

        if (!normalizedKey.isBlank()) {
            Optional<ApiIdempotencyEntity> existing = apiIdempotencyRepository
                    .findByTenantIdAndIdempotencyKeyAndOperation(tenantId, normalizedKey, OPERATION_CREATE_DOCUMENT);
            if (existing.isPresent()) {
                ApiIdempotencyEntity entity = existing.get();
                if (!entity.getRequestHash().equals(requestHash)) {
                    throw new IdempotencyKeyReusedException("The Idempotency-Key has already been used with a different request.");
                }
                return new CreateDocumentResult(toResponse(loadDte(entity.getDocumentId(), tenantId)), true);
            }
        }

        DteRequest dteRequest = buildDteRequest(request, tenant, normalizedKey);
        var metadata = dteService.ingest(dteRequest);
        Dte dte = loadDte(metadata.getDocumentId(), tenantId);

        if (!normalizedKey.isBlank()) {
            ApiIdempotencyEntity entity = new ApiIdempotencyEntity();
            entity.setTenantId(tenantId);
            entity.setIdempotencyKey(normalizedKey);
            entity.setOperation(OPERATION_CREATE_DOCUMENT);
            entity.setRequestHash(requestHash);
            entity.setDocumentId(dte.getId().toString());
            entity.setCreatedAt(Instant.now());
            try {
                apiIdempotencyRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException ex) {
                Optional<ApiIdempotencyEntity> concurrent = apiIdempotencyRepository
                        .findByTenantIdAndIdempotencyKeyAndOperation(tenantId, normalizedKey, OPERATION_CREATE_DOCUMENT);
                if (concurrent.isPresent()) {
                    ApiIdempotencyEntity previous = concurrent.get();
                    if (!previous.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyKeyReusedException("The Idempotency-Key has already been used with a different request.");
                    }
                    return new CreateDocumentResult(toResponse(loadDte(previous.getDocumentId(), tenantId)), true);
                }
                throw ex;
            }
        }

        return new CreateDocumentResult(toResponse(dte), false);
    }

    @Transactional(readOnly = true)
    public PublicDocumentResponse getDocument(String documentId, UUID tenantId) {
        Dte dte = loadDte(documentId, tenantId);
        return toResponse(dte);
    }

    @Transactional(readOnly = true)
    public PublicDocumentResponse getDocumentStatus(String documentId, UUID tenantId) {
        Dte dte = loadDte(documentId, tenantId);
        PublicDocumentResponse response = toResponse(dte);
        response.setStatus(dte.getSiiEstado() != null ? dte.getSiiEstado() : DocumentStatus.STORED.name());
        return response;
    }

    private DteRequest buildDteRequest(PublicDocumentRequest request, Tenant tenant, String idempotencyKey) {
        DteRequest dteRequest = new DteRequest();
        dteRequest.setDocumentId(UUID.randomUUID().toString());
        dteRequest.setEmitterRUT(request.getIssuer().getRutEnvia());
        dteRequest.setReceiverRUT(request.getReceiver().getRut());
        dteRequest.setAssignFolio(Boolean.TRUE);
        dteRequest.setTipoDte(33);
        dteRequest.setPuntoVenta(1);
        dteRequest.setRequestId(idempotencyKey.isBlank() ? dteRequest.getDocumentId() : idempotencyKey);
        dteRequest.setAssignedTo("PUBLIC_API");
        return dteRequest;
    }

    private Dte loadDte(String documentId, UUID tenantId) {
        return dteRepository.findByIdAndTenantId(UUID.fromString(documentId), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DOCUMENT_NOT_FOUND"));
    }

    private PublicDocumentResponse toResponse(Dte dte) {
        PublicDocumentResponse response = new PublicDocumentResponse();
        response.setDocumentId(dte.getId().toString());
        response.setType(dte.getTipoDte() == 33 ? "INVOICE" : "UNKNOWN");
        response.setStatus(dte.getSiiEstado() != null ? dte.getSiiEstado() : DocumentStatus.STORED.name());
        response.setFolio(dte.getFolio());
        response.setCreatedAt(Instant.ofEpochMilli(dte.getCreatedAt().toEpochMilli()).atOffset(java.time.ZoneOffset.UTC));

        PublicDocumentSiiStatus sii = new PublicDocumentSiiStatus();
        sii.setTrackId(dte.getSiiTrackId());
        sii.setStatus(dte.getSiiEstado());
        sii.setMessage(dte.getSiiGlosa());
        response.setSii(sii);
        return response;
    }

    private String hashRequest(PublicDocumentRequest request) {
        try {
            String normalized = objectMapper.writeValueAsString(request);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash request", ex);
        }
    }
}
