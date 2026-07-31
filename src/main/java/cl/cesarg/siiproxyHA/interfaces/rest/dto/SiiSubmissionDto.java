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
        OffsetDateTime nextAttemptAt,
        OffsetDateTime uploadedAt,
        OffsetDateTime completedAt,
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
                entity.getNextAttemptAt(),
                entity.getUploadedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
