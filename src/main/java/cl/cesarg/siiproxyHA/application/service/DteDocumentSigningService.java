package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates credential selection and the Documento XMLDSig operation.
 */
@Service
public class DteDocumentSigningService {

    private final SigningCredentialPort credentials;
    private final XmlSignerPort xmlSigner;

    public DteDocumentSigningService(
            SigningCredentialPort credentials,
            XmlSignerPort xmlSigner
    ) {
        this.credentials = credentials;
        this.xmlSigner = xmlSigner;
    }

    /**
     * Signs Documento and records credential use only after a validated signature.
     */
    public XmlSignerPort.SignedXml sign(
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

        XmlSignerPort.SignedXml signed = xmlSigner.sign(
                new XmlSignerPort.SigningRequest(
                        built.xml(),
                        built.documentoId(),
                        XmlSignerPort.SignatureTarget.DOCUMENTO,
                        credential,
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );
        credentials.recordSuccessfulUse(
                signed.credentialId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return signed;
    }
}
