package cl.cesarg.siiproxyHA.domain.port;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningContractsTest {

    @Test
    void signingRequestDefensivelyCopiesXml() {
        byte[] input = "<DTE/>".getBytes(StandardCharsets.UTF_8);
        XmlSignerPort.SigningRequest request = new XmlSignerPort.SigningRequest(
                input,
                "DTE-1",
                XmlSignerPort.SignatureTarget.DOCUMENTO,
                UUID.randomUUID(),
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );

        input[0] = 'X';
        byte[] returned = request.xml();
        returned[0] = 'Y';

        assertEquals('<', request.xml()[0]);
    }

    @Test
    void signedXmlOnlyAcceptsInternalReferences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new XmlSignerPort.SignedXml(
                        "<DTE/>".getBytes(StandardCharsets.UTF_8),
                        "https://example.test/document",
                        XmlSignerPort.SignatureTarget.DOCUMENTO,
                        UUID.randomUUID(),
                        XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
                )
        );
    }

    @Test
    void credentialContractsNormalizeRutAndRejectInvalidValidityRange() {
        UUID tenantId = UUID.randomUUID();
        SigningCredentialPort.SigningCredentialSelector selector =
                new SigningCredentialPort.SigningCredentialSelector(tenantId, "10.438.332-7");

        assertEquals("10438332-7", selector.signerRut());

        OffsetDateTime now = OffsetDateTime.now();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SigningCredentialPort.SigningCredentialDescriptor(
                        UUID.randomUUID(),
                        tenantId,
                        "10438332-7",
                        "123",
                        now,
                        now.minusDays(1)
                )
        );
    }

    @Test
    void cafDescriptorChecksAuthorizedRange() {
        CafMaterialPort.CafMaterialDescriptor descriptor =
                new CafMaterialPort.CafMaterialDescriptor(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "76.184.688-4",
                        33,
                        1,
                        182,
                        184,
                        LocalDate.of(2025, 12, 19)
                );

        assertTrue(descriptor.authorizes(182));
        assertTrue(descriptor.authorizes(184));
        assertFalse(descriptor.authorizes(185));
    }

    @Test
    void validationResultIsImmutableAndErrorsDetermineValidity() {
        DteXmlValidatorPort.ValidationIssue warning =
                new DteXmlValidatorPort.ValidationIssue(
                        "XSD_WARNING",
                        "Optional element is absent",
                        DteXmlValidatorPort.ValidationSeverity.WARNING,
                        null
                );
        DteXmlValidatorPort.ValidationResult validResult =
                new DteXmlValidatorPort.ValidationResult(List.of(warning));

        assertTrue(validResult.valid());
        assertThrows(UnsupportedOperationException.class, () -> validResult.issues().add(warning));

        DteXmlValidatorPort.ValidationIssue error =
                new DteXmlValidatorPort.ValidationIssue(
                        "SIGNATURE_INVALID",
                        "Signature validation failed",
                        DteXmlValidatorPort.ValidationSeverity.ERROR,
                        "#DTE-1"
                );

        assertFalse(new DteXmlValidatorPort.ValidationResult(List.of(error)).valid());
    }
}
