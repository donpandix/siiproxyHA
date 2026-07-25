package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves active PKCS#12 credentials from persisted tenant certificate metadata.
 */
@Component
public class Pkcs12SigningCredentialAdapter implements SigningCredentialPort {

    private static final String ACTIVE = "ACTIVE";

    private final UserCertificateRepository repository;
    private final Pkcs12SigningCredentialResolver resolver;

    public Pkcs12SigningCredentialAdapter(
            UserCertificateRepository repository,
            Pkcs12SigningCredentialResolver resolver
    ) {
        this.repository = repository;
        this.resolver = resolver;
    }

    @Override
    public SigningCredentialDescriptor requireSigningCredential(
            SigningCredentialSelector selector
    ) {
        Objects.requireNonNull(selector, "selector is required");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<UserCertificateEntity> candidates = repository
                .findByTenantIdAndStatus(selector.tenantId(), ACTIVE)
                .stream()
                .filter(entity -> selector.preferredCredentialId() == null
                        || selector.preferredCredentialId().equals(entity.getId()))
                .filter(entity -> selector.signerRut().equals(normalize(entity.getRutUsuario())))
                .filter(entity -> selector.signerRut().equals(normalize(entity.getCertSubjectRut())))
                .filter(entity -> entity.getValidFrom() != null
                        && !entity.getValidFrom().isAfter(now))
                .filter(entity -> entity.getValidUntil() != null
                        && !entity.getValidUntil().isBefore(now))
                .sorted(Comparator
                        .comparing(UserCertificateEntity::isDefault)
                        .reversed()
                        .thenComparing(
                                UserCertificateEntity::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                .toList();

        for (UserCertificateEntity candidate : candidates) {
            try {
                SigningCredentialDescriptor descriptor = toDescriptor(candidate);
                resolver.verifyCredential(descriptor);
                return descriptor;
            } catch (Pkcs12SigningCredentialResolver.CredentialLoadException exception) {
                if (exception.getFailure()
                        == Pkcs12SigningCredentialResolver.CredentialLoadFailure.STORAGE_UNAVAILABLE) {
                    throw unavailable(
                            CredentialFailureReason.DEPENDENCY_UNAVAILABLE,
                            "Signing credential storage is unavailable"
                    );
                }
                if (selector.preferredCredentialId() != null) {
                    throw unavailable(
                            CredentialFailureReason.NOT_SIGNING_CAPABLE,
                            "Preferred credential is not signing-capable"
                    );
                }
            } catch (SigningCredentialUnavailableException exception) {
                if (selector.preferredCredentialId() != null) {
                    throw unavailable(
                            CredentialFailureReason.NOT_SIGNING_CAPABLE,
                            "Preferred credential is not signing-capable"
                    );
                }
            }
        }

        CredentialFailureReason reason = candidates.isEmpty()
                ? CredentialFailureReason.NOT_FOUND
                : CredentialFailureReason.NOT_SIGNING_CAPABLE;
        throw unavailable(reason, "No active signing-capable credential is available");
    }

    @Override
    @Transactional
    public void recordSuccessfulUse(UUID credentialId, OffsetDateTime usedAt) {
        Objects.requireNonNull(credentialId, "credentialId is required");
        Objects.requireNonNull(usedAt, "usedAt is required");
        int updated = repository.recordSuccessfulUse(credentialId, usedAt);
        if (updated != 1) {
            throw unavailable(
                    CredentialFailureReason.STALE,
                    "Signing credential is no longer active"
            );
        }
    }

    private SigningCredentialDescriptor toDescriptor(UserCertificateEntity entity) {
        try {
            return new SigningCredentialDescriptor(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getCertSubjectRut(),
                    entity.getCertSerialNumber(),
                    entity.getValidFrom(),
                    entity.getValidUntil()
            );
        } catch (RuntimeException exception) {
            throw unavailable(
                    CredentialFailureReason.NOT_SIGNING_CAPABLE,
                    "Signing credential metadata is incomplete"
            );
        }
    }

    private String normalize(String rut) {
        try {
            return RutUtils.normalizeAndValidate(rut, "credentialRut");
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private SigningCredentialUnavailableException unavailable(
            CredentialFailureReason reason,
            String message
    ) {
        return new SigningCredentialUnavailableException(reason, message);
    }
}
