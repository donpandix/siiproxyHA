package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.application.service.SelfSignedCertGenerator;
import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort.SigningCredentialDescriptor;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Pkcs12SigningCredentialResolverTest {

    private static final String PASSWORD = "test-password";

    private UserCertificateRepository repository;
    private CertificateStoragePort storage;
    private CryptoService cryptoService;
    private Pkcs12SigningCredentialResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(UserCertificateRepository.class);
        storage = mock(CertificateStoragePort.class);
        cryptoService = mock(CryptoService.class);
        resolver = new Pkcs12SigningCredentialResolver(repository, storage, cryptoService);
    }

    @Test
    void resolvesRsaPrivateKeyAndClearsDownloadedBytes() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate certificate = certificate(keyPair);
        byte[] pkcs12 = pkcs12(keyPair, certificate, PASSWORD);
        byte[] downloaded = Arrays.copyOf(pkcs12, pkcs12.length);
        UserCertificateEntity entity = entity(certificate, "credential.p12");
        SigningCredentialDescriptor descriptor = descriptor(entity);

        when(repository.findByIdAndTenantId(entity.getId(), entity.getTenantId()))
                .thenReturn(Optional.of(entity));
        when(storage.get(entity.getCertificatePath())).thenReturn(downloaded);
        when(cryptoService.decrypt("encrypted-password", "iv")).thenReturn(PASSWORD);

        String algorithm = resolver.withCredential(
                descriptor,
                (privateKey, resolvedCertificate) -> {
                    assertEquals(certificate, resolvedCertificate);
                    return privateKey.getAlgorithm();
                }
        );

        assertEquals("RSA", algorithm);
        assertTrue(allZero(downloaded));
    }

    @Test
    void rejectsPublicOnlyCertificateFiles() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate certificate = certificate(keyPair);
        UserCertificateEntity entity = entity(certificate, "credential.crt");
        SigningCredentialDescriptor descriptor = descriptor(entity);

        when(repository.findByIdAndTenantId(entity.getId(), entity.getTenantId()))
                .thenReturn(Optional.of(entity));

        Pkcs12SigningCredentialResolver.CredentialLoadException exception =
                assertThrows(
                        Pkcs12SigningCredentialResolver.CredentialLoadException.class,
                        () -> resolver.verifyCredential(descriptor)
                );

        assertEquals(
                Pkcs12SigningCredentialResolver.CredentialLoadFailure.NOT_PKCS12,
                exception.getFailure()
        );
    }

    @Test
    void rejectsCredentialOutsideExpectedTenant() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate certificate = certificate(keyPair);
        UserCertificateEntity entity = entity(certificate, "credential.pfx");
        SigningCredentialDescriptor descriptor = new SigningCredentialDescriptor(
                entity.getId(),
                UUID.randomUUID(),
                entity.getCertSubjectRut(),
                entity.getCertSerialNumber(),
                entity.getValidFrom(),
                entity.getValidUntil()
        );

        when(repository.findByIdAndTenantId(descriptor.credentialId(), descriptor.tenantId()))
                .thenReturn(Optional.empty());

        Pkcs12SigningCredentialResolver.CredentialLoadException exception =
                assertThrows(
                        Pkcs12SigningCredentialResolver.CredentialLoadException.class,
                        () -> resolver.verifyCredential(descriptor)
                );

        assertEquals(
                Pkcs12SigningCredentialResolver.CredentialLoadFailure.NOT_FOUND,
                exception.getFailure()
        );
    }

    @Test
    void rejectsIncorrectPkcs12PasswordAndClearsDownloadedBytes() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate certificate = certificate(keyPair);
        byte[] downloaded = pkcs12(keyPair, certificate, PASSWORD);
        UserCertificateEntity entity = entity(certificate, "credential.p12");
        SigningCredentialDescriptor descriptor = descriptor(entity);

        when(repository.findByIdAndTenantId(entity.getId(), entity.getTenantId()))
                .thenReturn(Optional.of(entity));
        when(storage.get(entity.getCertificatePath())).thenReturn(downloaded);
        when(cryptoService.decrypt("encrypted-password", "iv")).thenReturn("incorrect");

        Pkcs12SigningCredentialResolver.CredentialLoadException exception =
                assertThrows(
                        Pkcs12SigningCredentialResolver.CredentialLoadException.class,
                        () -> resolver.verifyCredential(descriptor)
                );

        assertEquals(
                Pkcs12SigningCredentialResolver.CredentialLoadFailure.INVALID_PKCS12,
                exception.getFailure()
        );
        assertTrue(allZero(downloaded));
    }

    @Test
    void rejectsDescriptorWhoseCertificateMetadataChanged() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate certificate = certificate(keyPair);
        UserCertificateEntity entity = entity(certificate, "credential.p12");
        SigningCredentialDescriptor descriptor = new SigningCredentialDescriptor(
                entity.getId(),
                entity.getTenantId(),
                entity.getCertSubjectRut(),
                "different-serial",
                entity.getValidFrom(),
                entity.getValidUntil()
        );

        when(repository.findByIdAndTenantId(entity.getId(), entity.getTenantId()))
                .thenReturn(Optional.of(entity));

        Pkcs12SigningCredentialResolver.CredentialLoadException exception =
                assertThrows(
                        Pkcs12SigningCredentialResolver.CredentialLoadException.class,
                        () -> resolver.verifyCredential(descriptor)
                );

        assertEquals(
                Pkcs12SigningCredentialResolver.CredentialLoadFailure.STALE_DESCRIPTOR,
                exception.getFailure()
        );
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private X509Certificate certificate(KeyPair keyPair) throws Exception {
        return SelfSignedCertGenerator.generate(
                "CN=PKCS12 Test, SERIALNUMBER=10.438.332-7",
                keyPair
        );
    }

    private byte[] pkcs12(
            KeyPair keyPair,
            X509Certificate certificate,
            String password
    ) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] passwordChars = password.toCharArray();
        try {
            keyStore.load(null, passwordChars);
            keyStore.setKeyEntry(
                    "signing-key",
                    keyPair.getPrivate(),
                    passwordChars,
                    new Certificate[]{certificate}
            );
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                keyStore.store(output, passwordChars);
                return output.toByteArray();
            }
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private UserCertificateEntity entity(
            X509Certificate certificate,
            String filename
    ) {
        OffsetDateTime validFrom = OffsetDateTime.ofInstant(
                certificate.getNotBefore().toInstant(),
                ZoneOffset.UTC
        );
        OffsetDateTime validUntil = OffsetDateTime.ofInstant(
                certificate.getNotAfter().toInstant(),
                ZoneOffset.UTC
        );

        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setRutUsuario("10438332-7");
        entity.setCertSubjectRut("10438332-7");
        entity.setCertificatePath("tenants/test/certs/" + filename);
        entity.setEncryptedPassword("encrypted-password");
        entity.setEncryptionIv("iv");
        entity.setEncryptionAlgorithm("AES/GCM/NoPadding");
        entity.setCertSerialNumber(certificate.getSerialNumber().toString());
        entity.setValidFrom(validFrom);
        entity.setValidUntil(validUntil);
        entity.setStatus("ACTIVE");
        entity.setUsageCount(0);
        return entity;
    }

    private SigningCredentialDescriptor descriptor(UserCertificateEntity entity) {
        return new SigningCredentialDescriptor(
                entity.getId(),
                entity.getTenantId(),
                entity.getCertSubjectRut(),
                entity.getCertSerialNumber(),
                entity.getValidFrom(),
                entity.getValidUntil()
        );
    }

    private boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
