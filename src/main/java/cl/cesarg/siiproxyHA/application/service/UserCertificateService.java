package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.infrastructure.security.CertificateParser;
import cl.cesarg.siiproxyHA.infrastructure.security.CryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.UUID;

@Service
public class UserCertificateService {

    private final CertificateStoragePort storage;
    private final UserCertificateRepository repository;
    private final CryptoService cryptoService;
    private final TenantRepository tenantRepository;

    @Value("${storage.max-cert-size:5242880}")
    private long maxCertSize; // default 5MB

    private static final Set<String> ALLOWED_EXT = Set.of(".p12", ".pfx", ".pem", ".crt", ".cer");

    public UserCertificateService(CertificateStoragePort storage,
                                  UserCertificateRepository repository,
                                  CryptoService cryptoService,
                                  TenantRepository tenantRepository) {
        this.storage = storage;
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.tenantRepository = tenantRepository;
    }

    public UserCertificateEntity uploadCertificate(UUID tenantId,
                                                   String rutUsuario,
                                                   String nombreUsuario,
                                                   String createdBy,
                                                   String originalFilename,
                                                   InputStream content,
                                                   long size,
                                                   String contentType,
                                                   String password,
                                                   boolean isDefault) throws Exception {

        // verify tenant exists
        if (tenantId == null || tenantRepository.findById(tenantId).isEmpty()) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Empty file");
        }

        if (size > maxCertSize) {
            throw new IllegalArgumentException("File too large. Max allowed: " + maxCertSize + " bytes");
        }

        String name = originalFilename == null ? "" : originalFilename.toLowerCase();
        boolean okExt = ALLOWED_EXT.stream().anyMatch(name::endsWith);
        if (!okExt) {
            throw new IllegalArgumentException("Unsupported certificate file type: " + originalFilename);
        }

        byte[] bytes = content.readAllBytes();

        // compute SHA-256 hash (certificate fingerprint equivalent)
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        String hashHex = HexFormat.of().formatHex(hash);

        // duplicate check
        if (repository.existsByTenantIdAndCertificateHash(tenantId, hashHex)) {
            throw new IllegalArgumentException("Certificate already exists for tenant/user");
        }

        // parse certificate to extract metadata
        X509Certificate cert = CertificateParser.parse(bytes, originalFilename, password);
        if (cert == null) {
            throw new IllegalArgumentException("Unable to parse certificate");
        }

        OffsetDateTime validFrom = OffsetDateTime.ofInstant(cert.getNotBefore().toInstant(), ZoneOffset.UTC);
        OffsetDateTime validUntil = OffsetDateTime.ofInstant(cert.getNotAfter().toInstant(), ZoneOffset.UTC);

        if (validUntil.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("Certificate is expired");
        }

        String certSerial = cert.getSerialNumber() == null ? null : cert.getSerialNumber().toString();
        String issuer = cert.getIssuerX500Principal() == null ? null : cert.getIssuerX500Principal().getName();
        String subject = cert.getSubjectX500Principal() == null ? null : cert.getSubjectX500Principal().getName();
        String subjectRut = cl.cesarg.siiproxyHA.infrastructure.security.CertUtils
                .extractRutFromPrincipal(cert.getSubjectX500Principal());
        String normalizedRutUsuario = RutUtils.normalizeAndValidate(rutUsuario, "rutUsuario");
        String normalizedSubjectRut = RutUtils.normalizeAndValidate(subjectRut, "certSubjectRut");
        if (!normalizedRutUsuario.equals(normalizedSubjectRut)) {
            throw new IllegalArgumentException("rutUsuario does not match certificate subject RUT");
        }

        // encrypt password if present
        String encryptedPassword;
        String iv;
        String algorithm = "AES/GCM/NoPadding";
        if (password != null && !password.isBlank()) {
            CryptoService.EncryptedValue ev = cryptoService.encrypt(password);
            encryptedPassword = ev.ciphertext();
            iv = ev.iv();
        } else {
            encryptedPassword = "";
            iv = "";
        }

        UUID id = UUID.randomUUID();
        String key = String.format("tenants/%s/certs/%s/%s", tenantId.toString(), id.toString(), originalFilename);

        // store in object storage
        storage.store(key, new ByteArrayInputStream(bytes), bytes.length, contentType);

        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setRutUsuario(normalizedRutUsuario);
        entity.setNombreUsuario(nombreUsuario);
        entity.setCertificatePath(key);
        entity.setCertificateHash(hashHex);
        entity.setEncryptedPassword(encryptedPassword);
        entity.setEncryptionIv(iv);
        entity.setEncryptionAlgorithm(algorithm);
        entity.setCertSerialNumber(certSerial);
        entity.setCertIssuer(issuer);
        entity.setCertSubject(subject);
        entity.setCertSubjectRut(normalizedSubjectRut);
        entity.setValidFrom(validFrom);
        entity.setValidUntil(validUntil);
        entity.setStatus("ACTIVE");
        entity.setDefault(isDefault);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCreatedBy(createdBy);
        entity.setUsageCount(0);

        return repository.save(entity);
    }

    public UserCertificateEntity requireActiveCertificate(UUID tenantId, String rutEnvia) {
        String normalizedRutEnvia = RutUtils.normalizeAndValidate(rutEnvia, "rutEnvia");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return repository.findByTenantIdAndStatus(tenantId, "ACTIVE").stream()
                .filter(certificate -> normalizedRutEnvia.equals(RutUtils.normalize(certificate.getRutUsuario())))
                .filter(certificate -> normalizedRutEnvia.equals(RutUtils.normalize(certificate.getCertSubjectRut())))
                .filter(certificate -> certificate.getValidFrom() != null && !certificate.getValidFrom().isAfter(now))
                .filter(certificate -> certificate.getValidUntil() != null && !certificate.getValidUntil().isBefore(now))
                .sorted(Comparator.comparing(UserCertificateEntity::isDefault).reversed())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "rutEnvia is not registered with an active certificate for tenant"));
    }

    public List<UserCertificateEntity> listCertificates(UUID tenantId) {
        requireTenant(tenantId);

        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public UserCertificateEntity getCertificate(UUID tenantId, UUID certificateId) {
        requireTenant(tenantId);
        return findCertificate(tenantId, certificateId);
    }

    public void deleteCertificate(UUID tenantId, UUID certificateId) {
        requireTenant(tenantId);
        UserCertificateEntity certificate = findCertificate(tenantId, certificateId);

        // Keep the database record intact when object storage cannot complete the deletion.
        storage.delete(certificate.getCertificatePath());
        repository.delete(certificate);
        repository.flush();
    }

    private UserCertificateEntity findCertificate(UUID tenantId, UUID certificateId) {
        if (certificateId == null) {
            throw new ResourceNotFoundException("Certificate not found for tenant");
        }
        return repository.findByIdAndTenantId(certificateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found for tenant"));
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null || !tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }
}
