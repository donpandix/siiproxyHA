package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.DteXmlValidatorPort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DteXmlSigningServiceTest {

    @Mock
    private SigningCredentialPort credentials;
    @Mock
    private XmlSignerPort xmlSigner;
    @Mock
    private DteXmlValidatorPort xmlValidator;

    @Test
    void selectsCredentialOnceAndRecordsBothValidatedSignatures() {
        UUID tenantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        DteXmlBuilderPort.BuiltDteXml built = built();
        SigningCredentialPort.SigningCredentialDescriptor descriptor =
                descriptor(tenantId, credentialId);
        XmlSignerPort.SignedXml signedEnvelope = new XmlSignerPort.SignedXml(
                "<envelope-signed/>".getBytes(StandardCharsets.ISO_8859_1),
                "#SetDTE-test",
                XmlSignerPort.SignatureTarget.SET_DTE,
                credentialId,
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );
        when(credentials.requireSigningCredential(any())).thenReturn(descriptor);
        when(xmlSigner.signChain(any())).thenReturn(signedEnvelope);
        when(xmlValidator.validate(any())).thenReturn(validResult());
        DteXmlSigningService service =
                new DteXmlSigningService(credentials, xmlSigner, xmlValidator);

        XmlSignerPort.SignedXml result =
                service.signAll(built, tenantId, "10.438.332-7");

        ArgumentCaptor<SigningCredentialPort.SigningCredentialSelector> selector =
                ArgumentCaptor.forClass(SigningCredentialPort.SigningCredentialSelector.class);
        ArgumentCaptor<XmlSignerPort.ChainedSigningRequest> signingRequest =
                ArgumentCaptor.forClass(XmlSignerPort.ChainedSigningRequest.class);
        verify(credentials).requireSigningCredential(selector.capture());
        verify(xmlSigner).signChain(signingRequest.capture());
        verify(credentials, org.mockito.Mockito.times(2)).recordSuccessfulUse(
                org.mockito.ArgumentMatchers.eq(credentialId),
                any(OffsetDateTime.class)
        );
        assertEquals("10438332-7", selector.getValue().signerRut());
        assertEquals(tenantId, selector.getValue().tenantId());
        assertEquals("DTE-105", signingRequest.getValue().documentoId());
        assertEquals("SetDTE-test", signingRequest.getValue().setDteId());
        assertEquals(XmlSignerPort.SignatureTarget.SET_DTE, result.target());
        verify(xmlValidator).validate(any());
    }

    @Test
    void doesNotRecordUseWhenSignatureFails() {
        UUID tenantId = UUID.randomUUID();
        SigningCredentialPort.SigningCredentialDescriptor descriptor =
                descriptor(tenantId, UUID.randomUUID());
        when(credentials.requireSigningCredential(any())).thenReturn(descriptor);
        when(xmlSigner.signChain(any())).thenThrow(new XmlSignerPort.XmlSigningException(
                XmlSignerPort.XmlSigningFailureReason.SIGNATURE_INVALID,
                "invalid"
        ));
        DteXmlSigningService service =
                new DteXmlSigningService(credentials, xmlSigner, xmlValidator);

        assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> service.signAll(built(), tenantId, "10438332-7")
        );

        verify(credentials, never()).recordSuccessfulUse(any(), any());
    }

    @Test
    void doesNotRecordUseWhenAtomicSignatureChainFails() {
        UUID tenantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        SigningCredentialPort.SigningCredentialDescriptor descriptor =
                descriptor(tenantId, credentialId);
        when(credentials.requireSigningCredential(any())).thenReturn(descriptor);
        when(xmlSigner.signChain(any())).thenThrow(
                new XmlSignerPort.XmlSigningException(
                        XmlSignerPort.XmlSigningFailureReason.SIGNATURE_INVALID,
                        "invalid envelope"
                )
        );
        DteXmlSigningService service =
                new DteXmlSigningService(credentials, xmlSigner, xmlValidator);

        assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> service.signAll(built(), tenantId, "10438332-7")
        );

        verify(credentials, never()).recordSuccessfulUse(any(), any());
    }

    @Test
    void rejectsEnvelopeBeforeRecordingItsUseWhenIntegralValidationFails() {
        UUID tenantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        SigningCredentialPort.SigningCredentialDescriptor descriptor =
                descriptor(tenantId, credentialId);
        XmlSignerPort.SignedXml signedEnvelope = new XmlSignerPort.SignedXml(
                "<envelope-signed/>".getBytes(StandardCharsets.ISO_8859_1),
                "#SetDTE-test",
                XmlSignerPort.SignatureTarget.SET_DTE,
                credentialId,
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );
        when(credentials.requireSigningCredential(any())).thenReturn(descriptor);
        when(xmlSigner.signChain(any())).thenReturn(signedEnvelope);
        when(xmlValidator.validate(any())).thenReturn(
                new DteXmlValidatorPort.ValidationResult(List.of(
                        new DteXmlValidatorPort.ValidationIssue(
                                "XSD_VALIDATION",
                                "invalid",
                                DteXmlValidatorPort.ValidationSeverity.ERROR,
                                "#SetDTE-test"
                        )
                ))
        );
        DteXmlSigningService service =
                new DteXmlSigningService(credentials, xmlSigner, xmlValidator);

        assertThrows(
                DteXmlSigningService.DteXmlValidationException.class,
                () -> service.signAll(built(), tenantId, "10438332-7")
        );

        verify(credentials, never()).recordSuccessfulUse(any(), any());
    }

    private DteXmlValidatorPort.ValidationResult validResult() {
        return new DteXmlValidatorPort.ValidationResult(List.of());
    }

    private DteXmlBuilderPort.BuiltDteXml built() {
        return new DteXmlBuilderPort.BuiltDteXml(
                "<EnvioDTE/>".getBytes(StandardCharsets.ISO_8859_1),
                "DTE-105",
                "SetDTE-test",
                "ISO-8859-1"
        );
    }

    private SigningCredentialPort.SigningCredentialDescriptor descriptor(
            UUID tenantId,
            UUID credentialId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SigningCredentialPort.SigningCredentialDescriptor(
                credentialId,
                tenantId,
                "10438332-7",
                "123",
                now.minusDays(1),
                now.plusDays(1)
        );
    }
}
