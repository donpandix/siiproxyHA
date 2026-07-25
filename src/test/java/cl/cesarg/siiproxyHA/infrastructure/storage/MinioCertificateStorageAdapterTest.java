package cl.cesarg.siiproxyHA.infrastructure.storage;

import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MinioCertificateStorageAdapterTest {

    @Test
    void deletesCertificateFromConfiguredCertificateBucket() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioCertificateStorageAdapter adapter = new MinioCertificateStorageAdapter(
                client, "certificates-bucket");

        adapter.delete("tenants/tenant/certs/certificate.pfx");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("certificates-bucket");
        assertThat(captor.getValue().object()).isEqualTo("tenants/tenant/certs/certificate.pfx");
    }

    @Test
    void translatesMinioDeletionFailure() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new IllegalStateException("MinIO unavailable"))
                .when(client).removeObject(any(RemoveObjectArgs.class));
        MinioCertificateStorageAdapter adapter = new MinioCertificateStorageAdapter(
                client, "certificates-bucket");

        ObjectStorageException exception = assertThrows(ObjectStorageException.class,
                () -> adapter.delete("certificate.pfx"));

        assertThat(exception.getMessage()).contains("delete certificate");
    }
}
