package cl.cesarg.siiproxyHA.domain.model;

public enum SiiSubmissionStatus {
    PENDING_UPLOAD,
    UPLOADING,
    RECEIVED,
    STATUS_QUERYING,
    PROCESSED,
    REJECTED,
    OUTCOME_UNKNOWN,
    FAILED_RECOVERABLE,
    FAILED_FATAL
}
