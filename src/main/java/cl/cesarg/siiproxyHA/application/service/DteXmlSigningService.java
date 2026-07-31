package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.DteXmlValidatorPort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates credential selection and the chained DTE XMLDSig operations.
 */
@Service
public class DteXmlSigningService {

    private final SigningCredentialPort credentials;
    private final XmlSignerPort xmlSigner;
    private final DteXmlValidatorPort xmlValidator;

    public DteXmlSigningService(
            SigningCredentialPort credentials,
            XmlSignerPort xmlSigner,
            DteXmlValidatorPort xmlValidator
    ) {
        this.credentials = credentials;
        this.xmlSigner = xmlSigner;
        this.xmlValidator = xmlValidator;
    }

    /**
     * Signs Documento and SetDTE, then requires integral EnvioDTE validation.
     */
    public XmlSignerPort.SignedXml signAll(
            DteXmlBuilderPort.BuiltDteXml built,
            UUID tenantId,
            String signerRut
    ) {
        Objects.requireNonNull(built, "built is required");
        SigningCredentialPort.SigningCredentialDescriptor credential =
                credentials.requireSigningCredential(
                        new SigningCredentialPort.SigningCredentialSelector(
                                tenantId,
                                signerRut
                        )
                );

        XmlSignerPort.SignedXml signedEnvelope = xmlSigner.signChain(
                new XmlSignerPort.ChainedSigningRequest(
                        built.xml(),
                        built.documentoId(),
                        built.setDteId(),
                        credential,
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );
        DteXmlValidatorPort.ValidationResult validation = xmlValidator.validate(
                new DteXmlValidatorPort.ValidationRequest(
                        signedEnvelope.xml(),
                        DteXmlValidatorPort.ValidationProfile.ENVIO_DTE
                )
        );
        if (!validation.valid()) {
            throw new DteXmlValidationException(validation);
        }
        OffsetDateTime validatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        credentials.recordSuccessfulUse(
                signedEnvelope.credentialId(),
                validatedAt
        );
        credentials.recordSuccessfulUse(
                signedEnvelope.credentialId(),
                validatedAt
        );
        return signedEnvelope;
    }

    public static class DteXmlValidationException extends RuntimeException {

        private final DteXmlValidatorPort.ValidationResult validation;

        public DteXmlValidationException(
                DteXmlValidatorPort.ValidationResult validation
        ) {
            super("Generated EnvioDTE failed integral validation");
            this.validation = Objects.requireNonNull(validation);
        }

        public DteXmlValidatorPort.ValidationResult getValidation() {
            return validation;
        }
    }
}
