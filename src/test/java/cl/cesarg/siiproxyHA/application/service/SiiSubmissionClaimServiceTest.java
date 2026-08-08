package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.SiiSubmissionStatus;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiiSubmissionClaimServiceTest {

    @Test
    void outcomeUnknownIsClaimedForReconciliationInsteadOfUpload() {
        SiiSubmissionRepository repository = mock(SiiSubmissionRepository.class);
        SiiSubmissionEntity submission = submission(SiiSubmissionStatus.OUTCOME_UNKNOWN);
        when(repository.findNextClaimableId(any(), any())).thenReturn(Optional.of(submission.getId()));
        when(repository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SiiSubmissionClaimService.Claim claim = new SiiSubmissionClaimService(
                repository,
                new SiiProperties()
        ).claimNext().orElseThrow();

        assertEquals(SiiSubmissionClaimService.Operation.RECONCILIATION, claim.operation());
        assertEquals(SiiSubmissionStatus.RECONCILING, submission.getStatus());
        assertEquals(1, submission.getReconciliationCount());
        assertEquals(1, submission.getAttemptCount());
    }

    @Test
    void expiredUploadLeaseSchedulesReconciliationWithoutIncrementingUploadAttempts() {
        SiiSubmissionRepository repository = mock(SiiSubmissionRepository.class);
        SiiSubmissionEntity submission = submission(SiiSubmissionStatus.UPLOADING);
        when(repository.findNextClaimableId(any(), any())).thenReturn(Optional.of(submission.getId()));
        when(repository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SiiSubmissionClaimService.Claim claim = new SiiSubmissionClaimService(
                repository,
                new SiiProperties()
        ).claimNext().orElseThrow();

        assertEquals(SiiSubmissionClaimService.Operation.NONE, claim.operation());
        assertEquals(SiiSubmissionStatus.OUTCOME_UNKNOWN, submission.getStatus());
        assertEquals(1, submission.getAttemptCount());
        assertNotNull(submission.getOutcomeUnknownAt());
        assertNotNull(submission.getNextAttemptAt());
    }

    private SiiSubmissionEntity submission(SiiSubmissionStatus status) {
        SiiSubmissionEntity submission = new SiiSubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setStatus(status);
        submission.setAttemptCount(1);
        submission.setStatusQueryCount(0);
        submission.setReconciliationCount(0);
        submission.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        submission.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return submission;
    }
}
