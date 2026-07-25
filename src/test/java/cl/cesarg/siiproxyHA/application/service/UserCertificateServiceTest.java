package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.UserCertificateRepository;
import cl.cesarg.siiproxyHA.infrastructure.security.CryptoService;
import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCertificateServiceTest {

    private UserCertificateRepository repository;
    private TenantRepository tenantRepository;
    private CertificateStoragePort storage;
    private UserCertificateService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserCertificateRepository.class);
        tenantRepository = mock(TenantRepository.class);
        storage = mock(CertificateStoragePort.class);
        service = new UserCertificateService(
                storage,
                repository,
                mock(CryptoService.class),
                tenantRepository
        );
    }

    @Test
    void tenantCanHaveMultipleRutEnviaAndDefaultCertificateIsPreferred() {
        UUID tenantId = UUID.randomUUID();
        UserCertificateEntity otherSender = certificate("11111111-1", false);
        UserCertificateEntity oldCertificate = certificate("10438332-7", false);
        UserCertificateEntity defaultCertificate = certificate("10438332-7", true);
        when(repository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(otherSender, oldCertificate, defaultCertificate));

        UserCertificateEntity selected = service.requireActiveCertificate(tenantId, "10.438.332-7");

        assertSame(defaultCertificate, selected);
    }

    @Test
    void rejectsRutEnviaNotRegisteredForTenant() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(certificate("11111111-1", true)));

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveCertificate(tenantId, "10438332-7"));
    }

    @Test
    void listsTenantCertificatesInRepositoryOrder() {
        UUID tenantId = UUID.randomUUID();
        UserCertificateEntity newest = certificate("10438332-7", true);
        UserCertificateEntity oldest = certificate("11111111-1", false);
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(repository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(newest, oldest));

        List<UserCertificateEntity> result = service.listCertificates(tenantId);

        org.junit.jupiter.api.Assertions.assertEquals(List.of(newest, oldest), result);
    }

    @Test
    void rejectsCertificateListForUnknownTenant() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.listCertificates(tenantId));
    }

    @Test
    void getsCertificateOnlyWhenItBelongsToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        UserCertificateEntity certificate = certificate("10438332-7", true);
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(repository.findByIdAndTenantId(certificateId, tenantId))
                .thenReturn(Optional.of(certificate));

        assertSame(certificate, service.getCertificate(tenantId, certificateId));
    }

    @Test
    void rejectsCertificateOwnedByAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(repository.findByIdAndTenantId(certificateId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCertificate(tenantId, certificateId));
    }

    @Test
    void deletesObjectBeforeCertificateRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        UserCertificateEntity certificate = certificate("10438332-7", true);
        certificate.setCertificatePath("tenants/tenant/certs/certificate.pfx");
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(repository.findByIdAndTenantId(certificateId, tenantId))
                .thenReturn(Optional.of(certificate));

        service.deleteCertificate(tenantId, certificateId);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(storage, repository);
        order.verify(storage).delete(certificate.getCertificatePath());
        order.verify(repository).delete(certificate);
        order.verify(repository).flush();
    }

    @Test
    void keepsRecordWhenObjectStorageDeletionFails() {
        UUID tenantId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        UserCertificateEntity certificate = certificate("10438332-7", true);
        certificate.setCertificatePath("tenants/tenant/certs/certificate.pfx");
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(repository.findByIdAndTenantId(certificateId, tenantId))
                .thenReturn(Optional.of(certificate));
        doThrow(new ObjectStorageException("storage unavailable", new IllegalStateException()))
                .when(storage).delete(certificate.getCertificatePath());

        assertThrows(ObjectStorageException.class,
                () -> service.deleteCertificate(tenantId, certificateId));
        verify(repository, never()).delete(certificate);
    }

    private UserCertificateEntity certificate(String rut, boolean isDefault) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserCertificateEntity certificate = new UserCertificateEntity();
        certificate.setRutUsuario(rut);
        certificate.setCertSubjectRut(rut);
        certificate.setValidFrom(now.minusDays(1));
        certificate.setValidUntil(now.plusDays(1));
        certificate.setStatus("ACTIVE");
        certificate.setDefault(isDefault);
        return certificate;
    }
}
