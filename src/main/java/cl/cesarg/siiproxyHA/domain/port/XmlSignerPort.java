package cl.cesarg.siiproxyHA.domain.port;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Signs an XML element without exposing provider-specific key or DOM types.
 */
public interface XmlSignerPort {

    /**
     * Signs the requested XML element and returns a new XML artifact.
     */
    SignedXml sign(SigningRequest request);

    enum SignatureTarget {
        DOCUMENTO,
        SET_DTE
    }

    enum SignatureProfile {
        SII_LEGACY_RSA_SHA1
    }

    record SigningRequest(
            byte[] xml,
            String referenceId,
            SignatureTarget target,
            UUID credentialId,
            SignatureProfile profile
    ) {

        public SigningRequest {
            Objects.requireNonNull(xml, "xml is required");
            if (xml.length == 0) {
                throw new IllegalArgumentException("xml must not be empty");
            }
            if (referenceId == null || referenceId.isBlank()) {
                throw new IllegalArgumentException("referenceId is required");
            }
            referenceId = referenceId.trim();
            Objects.requireNonNull(target, "target is required");
            Objects.requireNonNull(credentialId, "credentialId is required");
            Objects.requireNonNull(profile, "profile is required");
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }

    record SignedXml(
            byte[] xml,
            String referenceUri,
            SignatureTarget target,
            UUID credentialId,
            SignatureProfile profile
    ) {

        public SignedXml {
            Objects.requireNonNull(xml, "xml is required");
            if (xml.length == 0) {
                throw new IllegalArgumentException("xml must not be empty");
            }
            if (referenceUri == null || referenceUri.isBlank()) {
                throw new IllegalArgumentException("referenceUri is required");
            }
            referenceUri = referenceUri.trim();
            if (!referenceUri.startsWith("#") || referenceUri.length() == 1) {
                throw new IllegalArgumentException("referenceUri must be an internal fragment");
            }
            Objects.requireNonNull(target, "target is required");
            Objects.requireNonNull(credentialId, "credentialId is required");
            Objects.requireNonNull(profile, "profile is required");
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }
}
