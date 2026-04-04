package cl.cesarg.siiproxyHA.domain.model;

public enum DocumentStatus {
    RECEIVED,
    VALIDATED,
    FOLIO_ASSIGNED,
    TED_GENERATED,
    SIGNED,
    STORED,
    ENQUEUED,
    SENT,
    FAILED_RECOVERABLE,
    FAILED_FATAL
}
