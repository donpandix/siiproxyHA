package cl.cesarg.siiproxyHA.domain.port;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Validates DTE XML artifacts without exposing parser or XMLDSig provider types.
 */
public interface DteXmlValidatorPort {

    /**
     * Validates an XML artifact against the selected DTE profile.
     */
    ValidationResult validate(ValidationRequest request);

    enum ValidationProfile {
        DTE_DOCUMENT,
        ENVIO_DTE
    }

    enum ValidationSeverity {
        WARNING,
        ERROR
    }

    record ValidationRequest(byte[] xml, ValidationProfile profile) {

        public ValidationRequest {
            Objects.requireNonNull(xml, "xml is required");
            if (xml.length == 0) {
                throw new IllegalArgumentException("xml must not be empty");
            }
            Objects.requireNonNull(profile, "profile is required");
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }

    record ValidationIssue(
            String code,
            String message,
            ValidationSeverity severity,
            String referenceUri
    ) {

        public ValidationIssue {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            code = code.trim();
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message is required");
            }
            message = message.trim();
            Objects.requireNonNull(severity, "severity is required");
            referenceUri = referenceUri == null || referenceUri.isBlank()
                    ? null
                    : referenceUri.trim();
        }
    }

    record ValidationResult(List<ValidationIssue> issues) {

        public ValidationResult {
            Objects.requireNonNull(issues, "issues is required");
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream()
                    .noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
        }
    }
}
