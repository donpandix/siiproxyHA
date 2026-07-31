package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DteStatusEvent;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.model.SiiSubmissionStatus;
import cl.cesarg.siiproxyHA.domain.port.SiiAuthenticationPort;
import cl.cesarg.siiproxyHA.domain.port.SiiStatusQueryPort;
import cl.cesarg.siiproxyHA.domain.port.SiiUploadPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteStatusEventRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class SiiSubmissionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SiiSubmissionProcessor.class);
    private static final long SMALL_FILE_LIMIT = 30L * 1024L;
    private static final Set<String> REJECTED_STATUS = Set.of("RSC", "RFR", "RCT");
    private static final Set<String> PENDING_STATUS = Set.of("PDR", "SOK", "CRT", "FOK");

    private final SiiSubmissionRepository submissions;
    private final DteRepository dteRepository;
    private final DteStatusEventRepository eventRepository;
    private final StoragePort storage;
    private final SiiAuthenticationPort authentication;
    private final SiiUploadPort upload;
    private final SiiStatusQueryPort statusQuery;
    private final SiiProperties properties;

    public SiiSubmissionProcessor(
            SiiSubmissionRepository submissions,
            DteRepository dteRepository,
            DteStatusEventRepository eventRepository,
            StoragePort storage,
            SiiAuthenticationPort authentication,
            SiiUploadPort upload,
            SiiStatusQueryPort statusQuery,
            SiiProperties properties
    ) {
        this.submissions = submissions;
        this.dteRepository = dteRepository;
        this.eventRepository = eventRepository;
        this.storage = storage;
        this.authentication = authentication;
        this.upload = upload;
        this.statusQuery = statusQuery;
        this.properties = properties;
    }

    public void process(SiiSubmissionClaimService.Claim claim) {
        if (claim.operation() == SiiSubmissionClaimService.Operation.NONE) {
            log.error(
                    "SII submission {} moved to OUTCOME_UNKNOWN after an expired upload lease",
                    claim.submissionId()
            );
            return;
        }
        SiiSubmissionEntity submission = submissions.findById(claim.submissionId())
                .orElseThrow(() -> new IllegalStateException("SII submission not found"));
        Dte dte = dteRepository.findWithTenantById(submission.getDteId())
                .orElseThrow(() -> new IllegalStateException("DTE for SII submission not found"));
        if (claim.operation() == SiiSubmissionClaimService.Operation.UPLOAD) {
            processUpload(submission, dte);
        } else {
            processStatusQuery(submission, dte);
        }
    }

    private void processUpload(SiiSubmissionEntity submission, Dte dte) {
        boolean uploadStarted = false;
        try {
            byte[] xml = storage.get(submission.getArtifactKey());
            requireArtifactIntegrity(submission, xml);
            RutParts sender = splitRut(dte.getRutEnvia(), "rutEnvia");
            RutParts company = splitRut(dte.getTenant().getRutEmisor(), "rutEmisor");
            String token = token(submission, dte);
            uploadStarted = true;
            SiiUploadPort.UploadResult result = upload.upload(
                    new SiiUploadPort.UploadRequest(
                            submission.getEnvironment(),
                            xml,
                            token,
                            sender.number(),
                            sender.dv(),
                            company.number(),
                            company.dv(),
                            "envio-" + dte.getId() + ".xml"
                    )
            );
            storeResponse(submission, "upload", result.rawResponse());
            submission.setRemoteHttpStatus(result.httpStatus());
            submission.setSiiStatus(result.status());
            submission.setSiiGlosa(truncate(result.reason(), 500));
            submission.setClaimedAt(null);
            OffsetDateTime now = now();
            if (result.received()) {
                submission.setTrackId(result.trackId());
                submission.setStatus(SiiSubmissionStatus.RECEIVED);
                submission.setUploadedAt(now);
                submission.setNextAttemptAt(now.plus(statusDelay(submission)));
                submission.setLastError(null);
                updateDte(dte, result.trackId(), result.status(), result.reason(), true);
                event(dte, "UPLOAD_RECEIVED", result.status(), result.reason(), submission);
                log.info(
                        "SII certification upload received submission={} dte={} trackId={} status={}",
                        submission.getId(),
                        dte.getId(),
                        result.trackId(),
                        result.status()
                );
            } else if ("5".equals(result.status())) {
                invalidateToken(submission, dte);
                submission.setLastError("SII rejected an inactive or missing token");
                scheduleSafeUploadRetry(submission, now);
                event(dte, "UPLOAD_TOKEN_REJECTED", result.status(), result.reason(), submission);
                log.warn(
                        "SII certification upload token rejected submission={} dte={}; token invalidated",
                        submission.getId(),
                        dte.getId()
                );
            } else {
                SiiSubmissionStatus failureStatus =
                        result.httpStatus() >= 500
                                ? SiiSubmissionStatus.OUTCOME_UNKNOWN
                                : SiiSubmissionStatus.REJECTED;
                submission.setStatus(failureStatus);
                submission.setCompletedAt(now);
                submission.setLastError(truncate(
                        "SII upload was not accepted"
                                + (result.reason() == null ? "" : ": " + result.reason()),
                        500
                ));
                event(dte, "UPLOAD_REJECTED", result.status(), result.reason(), submission);
                log.warn(
                        "SII certification upload not accepted submission={} dte={} http={} status={}",
                        submission.getId(),
                        dte.getId(),
                        result.httpStatus(),
                        result.status()
                );
            }
            submission.setUpdatedAt(now);
            submissions.save(submission);
        } catch (SiiTransportException exception) {
            handleUploadException(submission, dte, exception);
        } catch (Exception exception) {
            if (uploadStarted) {
                handlePostUploadFailure(submission, dte, exception);
            } else {
                handleSafeUploadFailure(submission, dte, exception);
            }
        }
    }

    private void processStatusQuery(SiiSubmissionEntity submission, Dte dte) {
        try {
            RutParts company = splitRut(dte.getTenant().getRutEmisor(), "rutEmisor");
            SiiStatusQueryPort.StatusResult result = statusQuery.query(
                    new SiiStatusQueryPort.StatusRequest(
                            submission.getEnvironment(),
                            company.number(),
                            company.dv(),
                            submission.getTrackId(),
                            token(submission, dte)
                    )
            );
            storeResponse(submission, "status", result.rawResponse());
            OffsetDateTime now = now();
            String status = normalizeStatus(result.status());
            submission.setRemoteHttpStatus(result.httpStatus());
            submission.setSiiStatus(status);
            submission.setSiiGlosa(truncate(result.glosa(), 500));
            submission.setNumeroAtencion(truncate(result.numeroAtencion(), 40));
            submission.setInformedCount(result.informedCount());
            submission.setAcceptedCount(result.acceptedCount());
            submission.setRejectedCount(result.rejectedCount());
            submission.setRepairCount(result.repairCount());
            submission.setClaimedAt(null);
            submission.setUpdatedAt(now);
            updateDte(dte, submission.getTrackId(), status, result.glosa(), false);
            event(dte, "STATUS_UPDATE", status, result.glosa(), submission);

            if (result.authenticationRejected()) {
                invalidateToken(submission, dte);
                retryStatusQuery(submission, "SII status query rejected the cached token");
            } else if ("EPR".equals(status)) {
                boolean containsRejectedDocuments =
                        result.rejectedCount() != null && result.rejectedCount() > 0;
                submission.setStatus(containsRejectedDocuments
                        ? SiiSubmissionStatus.REJECTED
                        : SiiSubmissionStatus.PROCESSED);
                submission.setCompletedAt(now);
                submission.setLastError(containsRejectedDocuments
                        ? "SII processed the submission with rejected documents"
                        : null);
                if (containsRejectedDocuments) {
                    log.warn(
                            "SII certification submission processed with rejected documents submission={} dte={} trackId={} rejected={}",
                            submission.getId(),
                            dte.getId(),
                            submission.getTrackId(),
                            result.rejectedCount()
                    );
                } else {
                    log.info(
                            "SII certification submission processed submission={} dte={} trackId={} accepted={} repairs={}",
                            submission.getId(),
                            dte.getId(),
                            submission.getTrackId(),
                            result.acceptedCount(),
                            result.repairCount()
                    );
                }
            } else if (REJECTED_STATUS.contains(status)) {
                submission.setStatus(SiiSubmissionStatus.REJECTED);
                submission.setCompletedAt(now);
                submission.setLastError(truncate(result.glosa(), 500));
                log.warn(
                        "SII certification submission rejected submission={} dte={} trackId={} status={}",
                        submission.getId(),
                        dte.getId(),
                        submission.getTrackId(),
                        status
                );
            } else if (PENDING_STATUS.contains(status)
                    && submission.getStatusQueryCount() < properties.getMaxStatusQueries()) {
                submission.setStatus(SiiSubmissionStatus.RECEIVED);
                submission.setNextAttemptAt(now.plus(statusDelay(submission)));
                submission.setLastError(null);
            } else {
                retryStatusQuery(
                        submission,
                        "Unexpected or unavailable SII status: " + status
                );
            }
            submissions.save(submission);
        } catch (Exception exception) {
            retryStatusQuery(submission, safeMessage(exception));
            event(dte, "STATUS_QUERY_FAILED", null, safeMessage(exception), submission);
            log.warn(
                    "SII certification status query failed submission={} dte={} attempt={} error={}",
                    submission.getId(),
                    dte.getId(),
                    submission.getStatusQueryCount(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void handleUploadException(
            SiiSubmissionEntity submission,
            Dte dte,
            SiiTransportException exception
    ) {
        OffsetDateTime now = now();
        submission.setClaimedAt(null);
        submission.setUpdatedAt(now);
        submission.setLastError(truncate(exception.getMessage(), 500));
        if (exception.isOutcomeUnknown()) {
            submission.setStatus(SiiSubmissionStatus.OUTCOME_UNKNOWN);
            submission.setCompletedAt(now);
        } else {
            scheduleSafeUploadRetry(submission, now);
        }
        submissions.save(submission);
        event(dte, "UPLOAD_TRANSPORT_FAILED", null, exception.getMessage(), submission);
        log.error(
                "SII certification upload transport failure submission={} dte={} outcomeUnknown={}",
                submission.getId(),
                dte.getId(),
                exception.isOutcomeUnknown()
        );
    }

    private void handleSafeUploadFailure(
            SiiSubmissionEntity submission,
            Dte dte,
            Exception exception
    ) {
        OffsetDateTime now = now();
        submission.setClaimedAt(null);
        submission.setUpdatedAt(now);
        submission.setLastError(truncate(safeMessage(exception), 500));
        scheduleSafeUploadRetry(submission, now);
        submissions.save(submission);
        event(dte, "UPLOAD_PREPARATION_FAILED", null, safeMessage(exception), submission);
        log.warn(
                "SII certification upload preparation failed submission={} dte={} attempt={} error={}",
                submission.getId(),
                dte.getId(),
                submission.getAttemptCount(),
                exception.getClass().getSimpleName()
        );
    }

    private void handlePostUploadFailure(
            SiiSubmissionEntity submission,
            Dte dte,
            Exception exception
    ) {
        OffsetDateTime now = now();
        submission.setClaimedAt(null);
        submission.setUpdatedAt(now);
        submission.setCompletedAt(now);
        submission.setStatus(SiiSubmissionStatus.OUTCOME_UNKNOWN);
        submission.setLastError(truncate(
                "Upload was invoked but its result could not be persisted: "
                        + safeMessage(exception),
                500
        ));
        try {
            submissions.save(submission);
            event(
                    dte,
                    "UPLOAD_RESULT_PERSISTENCE_FAILED",
                    null,
                    submission.getLastError(),
                    submission
            );
        } catch (Exception persistenceException) {
            log.error(
                    "Unable to persist OUTCOME_UNKNOWN for SII submission={} dte={}",
                    submission.getId(),
                    dte.getId(),
                    persistenceException
            );
        }
        log.error(
                "SII certification upload result persistence failed submission={} dte={}; automatic resend disabled",
                submission.getId(),
                dte.getId()
        );
    }

    private void scheduleSafeUploadRetry(SiiSubmissionEntity submission, OffsetDateTime now) {
        if (submission.getAttemptCount() >= 3) {
            submission.setStatus(SiiSubmissionStatus.FAILED_RECOVERABLE);
            submission.setCompletedAt(now);
        } else {
            submission.setStatus(SiiSubmissionStatus.PENDING_UPLOAD);
            submission.setNextAttemptAt(now.plusSeconds(30L * submission.getAttemptCount()));
        }
    }

    private void retryStatusQuery(SiiSubmissionEntity submission, String error) {
        OffsetDateTime now = now();
        submission.setClaimedAt(null);
        submission.setUpdatedAt(now);
        submission.setLastError(truncate(error, 500));
        if (submission.getStatusQueryCount() >= properties.getMaxStatusQueries()) {
            submission.setStatus(SiiSubmissionStatus.FAILED_RECOVERABLE);
            submission.setCompletedAt(now);
        } else {
            submission.setStatus(SiiSubmissionStatus.RECEIVED);
            submission.setNextAttemptAt(now.plus(statusDelay(submission)));
        }
        submissions.save(submission);
    }

    private String token(SiiSubmissionEntity submission, Dte dte) {
        return authentication.acquireToken(
                submission.getEnvironment(),
                dte.getTenant().getId(),
                dte.getRutEnvia(),
                submission.getSigningCredentialId()
        ).value();
    }

    private void invalidateToken(SiiSubmissionEntity submission, Dte dte) {
        authentication.invalidateToken(
                submission.getEnvironment(),
                dte.getTenant().getId(),
                dte.getRutEnvia(),
                submission.getSigningCredentialId()
        );
    }

    private void requireArtifactIntegrity(SiiSubmissionEntity submission, byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new IllegalStateException("Stored signed XML is empty");
        }
        if (xml.length != submission.getArtifactSizeBytes()) {
            throw new IllegalStateException("Stored signed XML size changed");
        }
        if (!sha256(xml).equals(submission.getArtifactSha256())) {
            throw new IllegalStateException("Stored signed XML checksum changed");
        }
    }

    private void storeResponse(SiiSubmissionEntity submission, String operation, byte[] response) {
        if (response == null || response.length == 0) {
            return;
        }
        String key = "sii-responses/%s/%s/%s-%d.xml".formatted(
                submission.getDteId(),
                submission.getId(),
                operation,
                System.currentTimeMillis()
        );
        try (ByteArrayInputStream input = new ByteArrayInputStream(response)) {
            submission.setResponseObjectKey(
                    storage.store(key, input, response.length, "application/xml")
            );
            submission.setResponseSha256(sha256(response));
        } catch (Exception exception) {
            log.warn(
                    "Unable to archive SII response submission={} operation={} error={}",
                    submission.getId(),
                    operation,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void updateDte(
            Dte dte,
            Long trackId,
            String status,
            String glosa,
            boolean uploaded
    ) {
        Instant now = Instant.now();
        dte.setSiiTrackId(trackId);
        dte.setSiiEstado(truncate(status, 10));
        dte.setSiiGlosa(truncate(glosa, 255));
        dte.setLastStatusAt(now);
        dte.setUpdatedAt(now);
        if (uploaded) {
            dte.setEnviadoAt(now);
        }
        dteRepository.save(dte);
    }

    private void event(
            Dte dte,
            String type,
            String code,
            String message,
            SiiSubmissionEntity submission
    ) {
        DteStatusEvent event = new DteStatusEvent();
        event.setId(UUID.randomUUID());
        event.setDte(dte);
        event.setEventType(type);
        event.setSiiCode(truncate(code, 10));
        event.setMessage(truncate(message, 500));
        event.setRawPayload(
                submission.getResponseObjectKey() == null
                        ? null
                        : "minio:" + submission.getResponseObjectKey()
        );
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
    }

    private Duration statusDelay(SiiSubmissionEntity submission) {
        return submission.getArtifactSizeBytes() < SMALL_FILE_LIMIT
                ? Duration.ofMinutes(2)
                : Duration.ofMinutes(6);
    }

    private RutParts splitRut(String value, String field) {
        String normalized = RutUtils.normalizeAndValidate(value, field);
        int dash = normalized.lastIndexOf('-');
        return new RutParts(
                normalized.substring(0, dash),
                normalized.substring(dash + 1)
        );
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record RutParts(String number, String dv) {}
}
