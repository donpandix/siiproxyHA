package cl.cesarg.siiproxyHA.domain.port;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        SigningCredentialPort.SigningCredentialDescriptor credential = credential();
        XmlSignerPort.SigningRequest request = new XmlSignerPort.SigningRequest(
                input,
                "DTE-1",
                XmlSignerPort.SignatureTarget.DOCUMENTO,
                credential,
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        );

        input[0] = 'X';
        byte[] returned = request.xml();
        returned[0] = 'Y';

        assertEquals('<', request.xml()[0]);
    }

    @Test
    void chainedSigningRequestDefensivelyCopiesXml() {
        byte[] input = "<EnvioDTE/>".getBytes(StandardCharsets.UTF_8);
        XmlSignerPort.ChainedSigningRequest request =
                new XmlSignerPort.ChainedSigningRequest(
                        input,
                        "DTE-1",
                        "SetDTE-1",
                        credential(),
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

    private SigningCredentialPort.SigningCredentialDescriptor credential() {
        OffsetDateTime now = OffsetDateTime.now();
        return new SigningCredentialPort.SigningCredentialDescriptor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "10438332-7",
                "123",
                now.minusDays(1),
                now.plusDays(1)
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
    void tedContractsNormalizeRutsAndDefensivelyCopyXml() {
        UUID tenantId = UUID.randomUUID();
        UUID cafId = UUID.randomUUID();
        TedGeneratorPort.TedRequest request = new TedGeneratorPort.TedRequest(
                tenantId,
                "10.438.332-7",
                33,
                1,
                100,
                cafId,
                LocalDate.of(2026, 7, 24),
                "60.803.000-K",
                "Receptor",
                10_000,
                "Primer item"
        );
        byte[] tedXml = "<TED/>".getBytes(StandardCharsets.ISO_8859_1);
        byte[] ddXml = "<DD/>".getBytes(StandardCharsets.ISO_8859_1);
        TedGeneratorPort.GeneratedTed generated = new TedGeneratorPort.GeneratedTed(
                tedXml,
                ddXml,
                LocalDateTime.of(2026, 7, 24, 12, 30, 45),
                cafId
        );

        tedXml[0] = 'X';
        ddXml[0] = 'Y';
        byte[] returnedTed = generated.tedXml();
        returnedTed[0] = 'Z';

        assertEquals("10438332-7", request.emitterRut());
        assertEquals("60803000-K", request.receiverRut());
        assertEquals('<', generated.tedXml()[0]);
        assertEquals('<', generated.ddXml()[0]);
    }

    @Test
    void domBuilderContractsCopyCollectionsAndXmlBytes() {
        TedGeneratorPort.GeneratedTed ted = new TedGeneratorPort.GeneratedTed(
                "<TED/>".getBytes(StandardCharsets.ISO_8859_1),
                "<DD/>".getBytes(StandardCharsets.ISO_8859_1),
                LocalDateTime.of(2026, 7, 24, 12, 30, 45),
                UUID.randomUUID()
        );
        DteXmlBuilderPort.ItemData item = new DteXmlBuilderPort.ItemData(
                1,
                "Item",
                "",
                1.0,
                1_000.0,
                1_000L
        );
        DteXmlBuilderPort.BuildRequest request = new DteXmlBuilderPort.BuildRequest(
                UUID.randomUUID(),
                new DteXmlBuilderPort.IssuerData(
                        "76184688-4",
                        "10438332-7",
                        "Emisor",
                        "Giro",
                        "726000",
                        "Dirección",
                        "Comuna",
                        LocalDate.of(2014, 8, 22),
                        80
                ),
                new DteXmlBuilderPort.ReceiverData(
                        "60803000-K",
                        "Receptor",
                        "Giro",
                        "Dirección",
                        "Comuna"
                ),
                new DteXmlBuilderPort.DocumentData(
                        33,
                        100,
                        LocalDate.of(2026, 7, 24),
                        1_000L,
                        new BigDecimal("19"),
                        190L,
                        1_190L
                ),
                List.of(item),
                List.of(),
                ted
        );
        byte[] xml = "<EnvioDTE/>".getBytes(StandardCharsets.ISO_8859_1);
        DteXmlBuilderPort.BuiltDteXml built = new DteXmlBuilderPort.BuiltDteXml(
                xml,
                "DTE-100",
                "SetDTE-1",
                "ISO-8859-1"
        );

        xml[0] = 'X';
        byte[] returned = built.xml();
        returned[0] = 'Y';

        assertThrows(
                UnsupportedOperationException.class,
                () -> request.items().add(item)
        );
        assertEquals('<', built.xml()[0]);
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
