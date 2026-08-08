package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SiiSubmissionDto(
        UUID id,
        UUID dteId,
        String environment,
        String status,
        Integer uploadAttempts,
        Integer statusQueries,
        Integer reconciliationAttempts,
        Long trackId,
        String siiStatus,
        String siiGlosa,
        String numeroAtencion,
        Integer informedCount,
        Integer acceptedCount,
        Integer rejectedCount,
        Integer repairCount,
        Integer remoteHttpStatus,
        String responseSha256,
        String lastError,
        String failureClass,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime uploadedAt,
        OffsetDateTime completedAt,
        OffsetDateTime outcomeUnknownAt,
        OffsetDateTime reconciledAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SiiSubmissionDto from(SiiSubmissionEntity entity) {
        return new SiiSubmissionDto(
                entity.getId(),
                entity.getDteId(),
                entity.getEnvironment(),
                entity.getStatus().name(),
                entity.getAttemptCount(),
                entity.getStatusQueryCount(),
                entity.getReconciliationCount(),
                entity.getTrackId(),
                entity.getSiiStatus(),
                entity.getSiiGlosa(),
                entity.getNumeroAtencion(),
                entity.getInformedCount(),
                entity.getAcceptedCount(),
                entity.getRejectedCount(),
                entity.getRepairCount(),
                entity.getRemoteHttpStatus(),
                entity.getResponseSha256(),
                entity.getLastError(),
                entity.getFailureClass(),
                entity.getNextAttemptAt(),
                entity.getUploadedAt(),
                entity.getCompletedAt(),
                entity.getOutcomeUnknownAt(),
                entity.getReconciledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
