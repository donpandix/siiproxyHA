package cl.cesarg.siiproxyHA.domain.port;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort.SigningCredentialDescriptor;

/**
 * Signs an XML element without exposing provider-specific key or DOM types.
 */
public interface XmlSignerPort {

    /**
     * Signs the requested XML element and returns a new XML artifact.
     */
    SignedXml sign(SigningRequest request);

    /**
     * Signs Documento in its SII legacy context, then SetDTE on the complete
     * envelope, and returns only the final serialized EnvioDTE.
     */
    SignedXml signChain(ChainedSigningRequest request);

    enum SignatureTarget {
        DOCUMENTO,
        SET_DTE
    }

    enum SignatureProfile {
        SII_LEGACY_RSA_SHA1
    }

    enum XmlSigningFailureReason {
        UNSUPPORTED_TARGET,
        INVALID_XML,
        TARGET_NOT_FOUND,
        AMBIGUOUS_TARGET,
        INVALID_STRUCTURE,
        ALREADY_SIGNED,
        SIGNATURE_INVALID,
        DD_CHANGED,
        SIGNING_FAILURE,
        SERIALIZATION_FAILURE
    }

    class XmlSigningException extends RuntimeException {

        private final XmlSigningFailureReason reason;

        public XmlSigningException(
                XmlSigningFailureReason reason,
                String message
        ) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public XmlSigningException(
                XmlSigningFailureReason reason,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public XmlSigningFailureReason getReason() {
            return reason;
        }
    }

    record SigningRequest(
            byte[] xml,
            String referenceId,
            SignatureTarget target,
            SigningCredentialDescriptor credential,
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
            Objects.requireNonNull(credential, "credential is required");
            Objects.requireNonNull(profile, "profile is required");
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }

    record ChainedSigningRequest(
            byte[] xml,
            String documentoId,
            String setDteId,
            SigningCredentialDescriptor credential,
            SignatureProfile profile
    ) {

        public ChainedSigningRequest {
            Objects.requireNonNull(xml, "xml is required");
            if (xml.length == 0) {
                throw new IllegalArgumentException("xml must not be empty");
            }
            if (documentoId == null || documentoId.isBlank()) {
                throw new IllegalArgumentException("documentoId is required");
            }
            if (setDteId == null || setDteId.isBlank()) {
                throw new IllegalArgumentException("setDteId is required");
            }
            documentoId = documentoId.trim();
            setDteId = setDteId.trim();
            Objects.requireNonNull(credential, "credential is required");
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
