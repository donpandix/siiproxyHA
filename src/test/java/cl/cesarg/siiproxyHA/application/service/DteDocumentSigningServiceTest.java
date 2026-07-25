package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DteDocumentSigningServiceTest {

    @Mock
    private SigningCredentialPort credentials;
    @Mock
    private XmlSignerPort xmlSigner;

    @Test
    void selectsRutEnviaAndRecordsUseAfterValidatedSignature() {
        UUID tenantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        DteXmlBuilderPort.BuiltDteXml built = built();
        SigningCredentialPort.SigningCredentialDescriptor descriptor =
                descriptor(tenantId, credentialId);
        XmlSignerPort.SignedXml signed = new XmlSignerPort.SignedXml(
                "<signed/>".getBytes(StandardCharsets.ISO_8859_1),
                "#DTE-105",
                XmlSignerPort.SignatureTarget.DOCUMENTO,
                credentialId,
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );
        when(credentials.requireSigningCredential(any())).thenReturn(descriptor);
        when(xmlSigner.sign(any())).thenReturn(signed);
        DteDocumentSigningService service =
                new DteDocumentSigningService(credentials, xmlSigner);

        XmlSignerPort.SignedXml result =
                service.sign(built, tenantId, "10.438.332-7");

        ArgumentCaptor<SigningCredentialPort.SigningCredentialSelector> selector =
                ArgumentCaptor.forClass(SigningCredentialPort.SigningCredentialSelector.class);
        ArgumentCaptor<XmlSignerPort.SigningRequest> signingRequest =
                ArgumentCaptor.forClass(XmlSignerPort.SigningRequest.class);
        verify(credentials).requireSigningCredential(selector.capture());
        verify(xmlSigner).sign(signingRequest.capture());
        verify(credentials).recordSuccessfulUse(
                org.mockito.ArgumentMatchers.eq(credentialId),
                any(OffsetDateTime.class)
        );
        assertEquals("10438332-7", selector.getValue().signerRut());
        assertEquals(tenantId, selector.getValue().tenantId());
        assertEquals("DTE-105", signingRequest.getValue().referenceId());
        assertEquals(XmlSignerPort.SignatureTarget.DOCUMENTO, result.target());
    }

    @Test
    void doesNotRecordUseWhenSignatureFails() {
        UUID tenantId = UUID.randomUUID();
        SigningCredentialPort.SigningCredentialDescriptor descriptor =
                descriptor(tenantId, UUID.randomUUID());
        when(credentials.requireSigningCredential(any())).thenReturn(descriptor);
        when(xmlSigner.sign(any())).thenThrow(new XmlSignerPort.XmlSigningException(
                XmlSignerPort.XmlSigningFailureReason.SIGNATURE_INVALID,
                "invalid"
        ));
        DteDocumentSigningService service =
                new DteDocumentSigningService(credentials, xmlSigner);

        assertThrows(
                XmlSignerPort.XmlSigningException.class,
                () -> service.sign(built(), tenantId, "10438332-7")
        );

        verify(credentials, never()).recordSuccessfulUse(any(), any());
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
