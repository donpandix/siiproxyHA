package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort.SigningCredentialDescriptor;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Opens PKCS#12 credentials for a bounded infrastructure operation.
 */
@Component
public class Pkcs12SigningCredentialResolver {

    private static final String ACTIVE = "ACTIVE";
    private static final String RSA = "RSA";
    private static final String PASSWORD_ENCRYPTION = "AES/GCM/NoPadding";
    private static final byte[] KEY_MATCH_CHALLENGE =
            "siiproxyHA-key-pair-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final UserCertificateRepository repository;
    private final CertificateStoragePort storage;
    private final CryptoService cryptoService;

    public Pkcs12SigningCredentialResolver(
            UserCertificateRepository repository,
            CertificateStoragePort storage,
            CryptoService cryptoService
    ) {
        this.repository = repository;
        this.storage = storage;
        this.cryptoService = cryptoService;
    }

    /**
     * Verifies that the descriptor currently resolves to a usable private credential.
     */
    public void verifyCredential(SigningCredentialDescriptor descriptor) {
        try {
            withCredential(descriptor, (privateKey, certificate) -> null);
        } catch (CredentialLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.INVALID_PKCS12,
                    "Unable to verify signing credential",
                    exception
            );
        }
    }

    /**
     * Runs one operation with a revalidated RSA private key and certificate.
     */
    public <T> T withCredential(
            SigningCredentialDescriptor descriptor,
            CredentialOperation<T> operation
    ) throws Exception {
        Objects.requireNonNull(descriptor, "descriptor is required");
        Objects.requireNonNull(operation, "operation is required");

        UserCertificateEntity entity = repository
                .findByIdAndTenantId(descriptor.credentialId(), descriptor.tenantId())
                .orElseThrow(() -> new CredentialLoadException(
                        CredentialLoadFailure.NOT_FOUND,
                        "Signing credential is not available"
                ));

        validateStoredMetadata(entity, descriptor);

        byte[] pkcs12Bytes = readCredential(entity);
        char[] password = null;
        try {
            password = decryptPassword(entity);
            LoadedCredential loaded = loadPkcs12(pkcs12Bytes, password);
            validateCertificate(entity, descriptor, loaded.privateKey(), loaded.certificate());
            return operation.execute(loaded.privateKey(), loaded.certificate());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            Arrays.fill(pkcs12Bytes, (byte) 0);
        }
    }

    private void validateStoredMetadata(
            UserCertificateEntity entity,
            SigningCredentialDescriptor descriptor
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!ACTIVE.equals(entity.getStatus())
                || entity.getValidFrom() == null
                || entity.getValidUntil() == null
                || entity.getValidFrom().isAfter(now)
                || entity.getValidUntil().isBefore(now)) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.INACTIVE,
                    "Signing credential is not active"
            );
        }

        String storedRut = normalizeRut(entity.getRutUsuario(), "rutUsuario");
        String subjectRut = normalizeRut(entity.getCertSubjectRut(), "certSubjectRut");
        if (!descriptor.signerRut().equals(storedRut)
                || !descriptor.signerRut().equals(subjectRut)
                || !Objects.equals(descriptor.certificateSerialNumber(), entity.getCertSerialNumber())
                || !Objects.equals(descriptor.validFrom(), entity.getValidFrom())
                || !Objects.equals(descriptor.validUntil(), entity.getValidUntil())) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.STALE_DESCRIPTOR,
                    "Signing credential metadata changed after selection"
            );
        }

        String path = entity.getCertificatePath();
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (!lowerPath.endsWith(".p12") && !lowerPath.endsWith(".pfx")) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.NOT_PKCS12,
                    "Signing credential must be PKCS#12 or PFX"
            );
        }
    }

    private byte[] readCredential(UserCertificateEntity entity) {
        try {
            byte[] bytes = storage.get(entity.getCertificatePath());
            if (bytes == null || bytes.length == 0) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.INVALID_PKCS12,
                        "Stored signing credential is empty"
                );
            }
            return bytes;
        } catch (CredentialLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.STORAGE_UNAVAILABLE,
                    "Unable to read signing credential",
                    exception
            );
        }
    }

    private char[] decryptPassword(UserCertificateEntity entity) {
        String encryptedPassword = entity.getEncryptedPassword();
        String iv = entity.getEncryptionIv();
        if ((encryptedPassword == null || encryptedPassword.isBlank())
                && (iv == null || iv.isBlank())) {
            return new char[0];
        }
        if (encryptedPassword == null || encryptedPassword.isBlank()
                || iv == null || iv.isBlank()
                || !PASSWORD_ENCRYPTION.equals(entity.getEncryptionAlgorithm())) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.INVALID_PASSWORD_METADATA,
                    "Signing credential password metadata is invalid"
            );
        }

        try {
            String plaintext = cryptoService.decrypt(encryptedPassword, iv);
            if (plaintext == null) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.INVALID_PASSWORD,
                        "Decrypted signing credential password is unavailable"
                );
            }
            return plaintext.toCharArray();
        } catch (Exception exception) {
            if (exception instanceof CredentialLoadException credentialLoadException) {
                throw credentialLoadException;
            }
            throw new CredentialLoadException(
                    CredentialLoadFailure.INVALID_PASSWORD,
                    "Unable to decrypt signing credential password",
                    exception
            );
        }
    }

    private LoadedCredential loadPkcs12(byte[] bytes, char[] password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new java.io.ByteArrayInputStream(bytes), password);

            List<LoadedCredential> privateEntries = new ArrayList<>();
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!keyStore.isKeyEntry(alias)) {
                    continue;
                }
                Key key = keyStore.getKey(alias, password);
                Certificate certificate = keyStore.getCertificate(alias);
                if (key instanceof PrivateKey privateKey
                        && certificate instanceof X509Certificate x509Certificate) {
                    privateEntries.add(new LoadedCredential(privateKey, x509Certificate));
                }
            }

            if (privateEntries.isEmpty()) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.NO_PRIVATE_KEY,
                        "PKCS#12 does not contain a private key entry"
                );
            }
            if (privateEntries.size() > 1) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.AMBIGUOUS_PRIVATE_KEY,
                        "PKCS#12 contains more than one private key entry"
                );
            }
            return privateEntries.getFirst();
        } catch (CredentialLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.INVALID_PKCS12,
                    "Unable to open PKCS#12 signing credential",
                    exception
            );
        }
    }

    private void validateCertificate(
            UserCertificateEntity entity,
            SigningCredentialDescriptor descriptor,
            PrivateKey privateKey,
            X509Certificate certificate
    ) {
        try {
            certificate.checkValidity(Date.from(java.time.Instant.now()));
            if (!RSA.equalsIgnoreCase(privateKey.getAlgorithm())
                    || !RSA.equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.UNSUPPORTED_KEY,
                        "Signing credential must use RSA"
                );
            }
            boolean[] keyUsage = certificate.getKeyUsage();
            if (keyUsage != null && (keyUsage.length == 0 || !keyUsage[0])) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.INVALID_KEY_USAGE,
                        "Certificate does not allow digital signatures"
                );
            }

            String certificateRut = normalizeRut(
                    CertUtils.extractRutFromPrincipal(certificate.getSubjectX500Principal()),
                    "certificateSubjectRut"
            );
            if (!descriptor.signerRut().equals(certificateRut)
                    || !certificateRut.equals(normalizeRut(entity.getCertSubjectRut(), "certSubjectRut"))
                    || !certificate.getSerialNumber().toString()
                    .equals(descriptor.certificateSerialNumber())) {
                throw new CredentialLoadException(
                        CredentialLoadFailure.CERTIFICATE_MISMATCH,
                        "PKCS#12 certificate does not match stored metadata"
                );
            }

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(KEY_MATCH_CHALLENGE);
            byte[] signature = signer.sign();
            try {
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(certificate.getPublicKey());
                verifier.update(KEY_MATCH_CHALLENGE);
                if (!verifier.verify(signature)) {
                    throw new CredentialLoadException(
                            CredentialLoadFailure.KEY_MISMATCH,
                            "Private key does not match certificate"
                    );
                }
            } finally {
                Arrays.fill(signature, (byte) 0);
            }
        } catch (CredentialLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.INVALID_CERTIFICATE,
                    "Signing certificate validation failed",
                    exception
            );
        }
    }

    private String normalizeRut(String rut, String field) {
        try {
            return RutUtils.normalizeAndValidate(rut, field);
        } catch (IllegalArgumentException exception) {
            throw new CredentialLoadException(
                    CredentialLoadFailure.CERTIFICATE_MISMATCH,
                    "Signing credential contains invalid RUT metadata",
                    exception
            );
        }
    }

    @FunctionalInterface
    public interface CredentialOperation<T> {
        T execute(PrivateKey privateKey, X509Certificate certificate) throws Exception;
    }

    public enum CredentialLoadFailure {
        NOT_FOUND,
        INACTIVE,
        STALE_DESCRIPTOR,
        NOT_PKCS12,
        STORAGE_UNAVAILABLE,
        INVALID_PASSWORD_METADATA,
        INVALID_PASSWORD,
        INVALID_PKCS12,
        NO_PRIVATE_KEY,
        AMBIGUOUS_PRIVATE_KEY,
        UNSUPPORTED_KEY,
        INVALID_KEY_USAGE,
        CERTIFICATE_MISMATCH,
        KEY_MISMATCH,
        INVALID_CERTIFICATE
    }

    public static class CredentialLoadException extends RuntimeException {

        private final CredentialLoadFailure failure;

        public CredentialLoadException(CredentialLoadFailure failure, String message) {
            super(message);
            this.failure = Objects.requireNonNull(failure, "failure is required");
        }

        public CredentialLoadException(
                CredentialLoadFailure failure,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.failure = Objects.requireNonNull(failure, "failure is required");
        }

        public CredentialLoadFailure getFailure() {
            return failure;
        }
    }

    private record LoadedCredential(PrivateKey privateKey, X509Certificate certificate) {
    }
}
