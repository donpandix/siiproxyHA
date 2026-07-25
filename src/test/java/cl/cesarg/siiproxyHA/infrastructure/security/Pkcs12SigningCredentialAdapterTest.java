package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.port.SigningCredentialPort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Pkcs12SigningCredentialAdapterTest {

    private UserCertificateRepository repository;
    private Pkcs12SigningCredentialResolver resolver;
    private Pkcs12SigningCredentialAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(UserCertificateRepository.class);
        resolver = mock(Pkcs12SigningCredentialResolver.class);
        adapter = new Pkcs12SigningCredentialAdapter(repository, resolver);
    }

    @Test
    void prefersDefaultSigningCapableCredential() {
        UUID tenantId = UUID.randomUUID();
        UserCertificateEntity nonDefault = credential(tenantId, false);
        UserCertificateEntity preferred = credential(tenantId, true);
        when(repository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(nonDefault, preferred));

        SigningCredentialPort.SigningCredentialDescriptor result =
                adapter.requireSigningCredential(
                        new SigningCredentialPort.SigningCredentialSelector(
                                tenantId,
                                "10.438.332-7"
                        )
                );

        assertEquals(preferred.getId(), result.credentialId());
        verify(resolver).verifyCredential(result);
    }

    @Test
    void rejectsPreferredCredentialOwnedByAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID preferredId = UUID.randomUUID();
        UserCertificateEntity other = credential(tenantId, true);
        when(repository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(other));

        SigningCredentialPort.SigningCredentialUnavailableException exception =
                assertThrows(
                        SigningCredentialPort.SigningCredentialUnavailableException.class,
                        () -> adapter.requireSigningCredential(
                                new SigningCredentialPort.SigningCredentialSelector(
                                        tenantId,
                                        "10438332-7",
                                        preferredId
                                )
                        )
                );

        assertEquals(
                SigningCredentialPort.CredentialFailureReason.NOT_FOUND,
                exception.getReason()
        );
    }

    @Test
    void skipsDefaultCredentialThatCannotSign() {
        UUID tenantId = UUID.randomUUID();
        UserCertificateEntity fallback = credential(tenantId, false);
        UserCertificateEntity invalidDefault = credential(tenantId, true);
        when(repository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(fallback, invalidDefault));
        doThrow(new Pkcs12SigningCredentialResolver.CredentialLoadException(
                Pkcs12SigningCredentialResolver.CredentialLoadFailure.NOT_PKCS12,
                "not signing-capable"
        )).when(resolver).verifyCredential(argThat(
                descriptor -> invalidDefault.getId().equals(descriptor.credentialId())
        ));

        SigningCredentialPort.SigningCredentialDescriptor result =
                adapter.requireSigningCredential(
                        new SigningCredentialPort.SigningCredentialSelector(
                                tenantId,
                                "10438332-7"
                        )
                );

        assertEquals(fallback.getId(), result.credentialId());
        verify(resolver).verifyCredential(result);
    }

    @Test
    void reportsCredentialStorageOutageWithoutFallingBack() {
        UUID tenantId = UUID.randomUUID();
        UserCertificateEntity candidate = credential(tenantId, true);
        when(repository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(candidate));
        doThrow(new Pkcs12SigningCredentialResolver.CredentialLoadException(
                Pkcs12SigningCredentialResolver.CredentialLoadFailure.STORAGE_UNAVAILABLE,
                "storage unavailable"
        )).when(resolver).verifyCredential(argThat(
                descriptor -> candidate.getId().equals(descriptor.credentialId())
        ));

        SigningCredentialPort.SigningCredentialUnavailableException exception =
                assertThrows(
                        SigningCredentialPort.SigningCredentialUnavailableException.class,
                        () -> adapter.requireSigningCredential(
                                new SigningCredentialPort.SigningCredentialSelector(
                                        tenantId,
                                        "10438332-7"
                                )
                        )
                );

        assertEquals(
                SigningCredentialPort.CredentialFailureReason.DEPENDENCY_UNAVAILABLE,
                exception.getReason()
        );
    }

    @Test
    void recordsUsageAtomicallyOnlyWhenCredentialRemainsActive() {
        UUID credentialId = UUID.randomUUID();
        OffsetDateTime usedAt = OffsetDateTime.now(ZoneOffset.UTC);
        when(repository.recordSuccessfulUse(credentialId, usedAt)).thenReturn(1);

        adapter.recordSuccessfulUse(credentialId, usedAt);

        verify(repository).recordSuccessfulUse(credentialId, usedAt);
    }

    @Test
    void rejectsUsageUpdateForStaleCredential() {
        UUID credentialId = UUID.randomUUID();
        OffsetDateTime usedAt = OffsetDateTime.now(ZoneOffset.UTC);
        when(repository.recordSuccessfulUse(credentialId, usedAt)).thenReturn(0);

        SigningCredentialPort.SigningCredentialUnavailableException exception =
                assertThrows(
                        SigningCredentialPort.SigningCredentialUnavailableException.class,
                        () -> adapter.recordSuccessfulUse(credentialId, usedAt)
                );

        assertEquals(
                SigningCredentialPort.CredentialFailureReason.STALE,
                exception.getReason()
        );
    }

    private UserCertificateEntity credential(UUID tenantId, boolean isDefault) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setRutUsuario("10438332-7");
        entity.setCertSubjectRut("10438332-7");
        entity.setCertSerialNumber(UUID.randomUUID().toString());
        entity.setValidFrom(now.minusDays(1));
        entity.setValidUntil(now.plusDays(1));
        entity.setStatus("ACTIVE");
        entity.setDefault(isDefault);
        entity.setCreatedAt(now);
        return entity;
    }
}
