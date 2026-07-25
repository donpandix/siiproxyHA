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
 * Coordinates credential selection and the chained DTE XMLDSig operations.
 */
@Service
public class DteXmlSigningService {

    private final SigningCredentialPort credentials;
    private final XmlSignerPort xmlSigner;

    public DteXmlSigningService(
            SigningCredentialPort credentials,
            XmlSignerPort xmlSigner
    ) {
        this.credentials = credentials;
        this.xmlSigner = xmlSigner;
    }

    /**
     * Signs Documento and SetDTE in order, recording each validated operation.
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

        XmlSignerPort.SignedXml signedDocument = xmlSigner.sign(
                new XmlSignerPort.SigningRequest(
                        built.xml(),
                        built.documentoId(),
                        XmlSignerPort.SignatureTarget.DOCUMENTO,
                        credential,
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );
        credentials.recordSuccessfulUse(
                signedDocument.credentialId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        XmlSignerPort.SignedXml signedEnvelope = xmlSigner.sign(
                new XmlSignerPort.SigningRequest(
                        signedDocument.xml(),
                        built.setDteId(),
                        XmlSignerPort.SignatureTarget.SET_DTE,
                        credential,
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );
        credentials.recordSuccessfulUse(
                signedEnvelope.credentialId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return signedEnvelope;
    }
}
