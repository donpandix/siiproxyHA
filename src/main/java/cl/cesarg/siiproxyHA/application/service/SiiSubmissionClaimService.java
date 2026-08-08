package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.SiiSubmissionStatus;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class SiiSubmissionClaimService {

    private final SiiSubmissionRepository repository;
    private final SiiProperties properties;

    public SiiSubmissionClaimService(
            SiiSubmissionRepository repository,
            SiiProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public Optional<Claim> claimNext() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<UUID> id = repository.findNextClaimableId(
                now,
                now.minus(properties.getClaimLease())
        );
        if (id.isEmpty()) {
            return Optional.empty();
        }
        SiiSubmissionEntity submission = repository.findById(id.get())
                .orElseThrow(() -> new IllegalStateException("Claimed SII submission disappeared"));
        SiiSubmissionStatus previous = submission.getStatus();
        if (previous == SiiSubmissionStatus.UPLOADING) {
            submission.setStatus(SiiSubmissionStatus.OUTCOME_UNKNOWN);
            submission.setLastError(
                    "Worker lease expired during upload; reconciliation required before resend"
            );
            submission.setFailureClass("OUTCOME_UNKNOWN");
            submission.setOutcomeUnknownAt(now);
            submission.setNextAttemptAt(now.plus(properties.getReconciliationInitialDelay()));
            submission.setClaimedAt(null);
            submission.setUpdatedAt(now);
            repository.save(submission);
            return Optional.of(new Claim(submission.getId(), Operation.NONE));
        }
        Operation operation = switch (previous) {
            case PENDING_UPLOAD -> Operation.UPLOAD;
            case OUTCOME_UNKNOWN, RECONCILING -> Operation.RECONCILIATION;
            default -> Operation.STATUS_QUERY;
        };
        submission.setStatus(switch (operation) {
            case UPLOAD -> SiiSubmissionStatus.UPLOADING;
            case STATUS_QUERY -> SiiSubmissionStatus.STATUS_QUERYING;
            case RECONCILIATION -> SiiSubmissionStatus.RECONCILING;
            case NONE -> throw new IllegalStateException("NONE cannot claim a submission");
        });
        submission.setClaimedAt(now);
        submission.setUpdatedAt(now);
        if (operation == Operation.UPLOAD) {
            submission.setAttemptCount(count(submission.getAttemptCount()) + 1);
        } else if (operation == Operation.STATUS_QUERY) {
            submission.setStatusQueryCount(count(submission.getStatusQueryCount()) + 1);
        } else {
            submission.setReconciliationCount(count(submission.getReconciliationCount()) + 1);
        }
        repository.save(submission);
        return Optional.of(new Claim(submission.getId(), operation));
    }

    public enum Operation {
        UPLOAD,
        STATUS_QUERY,
        RECONCILIATION,
        NONE
    }

    public record Claim(UUID submissionId, Operation operation) {}

    private int count(Integer value) {
        return value == null ? 0 : value;
    }
}
