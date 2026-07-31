package cl.cesarg.siiproxyHA.infrastructure.sii;

public class SiiTransportException extends RuntimeException {

    private final boolean outcomeUnknown;

    public SiiTransportException(String message, boolean outcomeUnknown, Throwable cause) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
