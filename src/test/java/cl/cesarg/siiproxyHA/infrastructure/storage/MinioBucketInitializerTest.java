package cl.cesarg.siiproxyHA.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class MinioBucketInitializerTest {

    @Test
    void createsBothBucketsWhenTheyDoNotExist() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MinioBucketInitializer initializer = new MinioBucketInitializer(
                client, "dte-bucket", "certificates-bucket");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<MakeBucketArgs> captor = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(client, times(2)).makeBucket(captor.capture());
        assertThat(captor.getAllValues()).extracting(MakeBucketArgs::bucket)
                .containsExactly("dte-bucket", "certificates-bucket");
    }

    @Test
    void doesNotRecreateExistingBuckets() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MinioBucketInitializer initializer = new MinioBucketInitializer(
                client, "dte-bucket", "certificates-bucket");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void reportsWhichBucketCouldNotBeInitialized() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IllegalStateException("MinIO unavailable"));
        MinioBucketInitializer initializer = new MinioBucketInitializer(
                client, "dte-bucket", "certificates-bucket");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0])));

        assertThat(exception.getMessage()).contains("dte-bucket");
    }

    @Test
    void acceptsBucketCreatedConcurrentlyByAnotherInstance() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class)))
                .thenReturn(false, true, true);
        doThrow(new IllegalStateException("Bucket already exists"))
                .when(client).makeBucket(any(MakeBucketArgs.class));
        MinioBucketInitializer initializer = new MinioBucketInitializer(
                client, "shared-bucket", "shared-bucket");

        assertDoesNotThrow(() -> initializer.run(new DefaultApplicationArguments(new String[0])));
    }
}
