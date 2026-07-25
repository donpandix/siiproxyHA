package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Selects signing-capable credentials while keeping private material in infrastructure.
 */
public interface SigningCredentialPort {

    /**
     * Resolves one active credential authorized for the requested tenant and signer RUT.
     */
    SigningCredentialDescriptor requireSigningCredential(SigningCredentialSelector selector);

    /**
     * Records usage only after the caller completes a signature successfully.
     */
    void recordSuccessfulUse(UUID credentialId, OffsetDateTime usedAt);

    enum CredentialFailureReason {
        NOT_FOUND,
        NOT_SIGNING_CAPABLE,
        DEPENDENCY_UNAVAILABLE,
        STALE
    }

    class SigningCredentialUnavailableException extends RuntimeException {

        private final CredentialFailureReason reason;

        public SigningCredentialUnavailableException(
                CredentialFailureReason reason,
                String message
        ) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public CredentialFailureReason getReason() {
            return reason;
        }
    }

    record SigningCredentialSelector(
            UUID tenantId,
            String signerRut,
            UUID preferredCredentialId
    ) {

        public SigningCredentialSelector(UUID tenantId, String signerRut) {
            this(tenantId, signerRut, null);
        }

        public SigningCredentialSelector {
            Objects.requireNonNull(tenantId, "tenantId is required");
            signerRut = RutUtils.normalizeAndValidate(signerRut, "signerRut");
        }
    }

    record SigningCredentialDescriptor(
            UUID credentialId,
            UUID tenantId,
            String signerRut,
            String certificateSerialNumber,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil
    ) {

        public SigningCredentialDescriptor {
            Objects.requireNonNull(credentialId, "credentialId is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            signerRut = RutUtils.normalizeAndValidate(signerRut, "signerRut");
            if (certificateSerialNumber == null || certificateSerialNumber.isBlank()) {
                throw new IllegalArgumentException("certificateSerialNumber is required");
            }
            certificateSerialNumber = certificateSerialNumber.trim();
            Objects.requireNonNull(validFrom, "validFrom is required");
            Objects.requireNonNull(validUntil, "validUntil is required");
            if (validUntil.isBefore(validFrom)) {
                throw new IllegalArgumentException("validUntil must not be before validFrom");
            }
        }
    }
}
