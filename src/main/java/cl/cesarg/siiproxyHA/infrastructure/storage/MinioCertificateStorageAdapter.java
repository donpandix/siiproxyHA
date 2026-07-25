package cl.cesarg.siiproxyHA.infrastructure.storage;

import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import cl.cesarg.siiproxyHA.domain.port.CertificateStoragePort;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioCertificateStorageAdapter implements CertificateStoragePort {

    private final MinioClient client;
    private final String bucket;

    public MinioCertificateStorageAdapter(MinioClient client,
                                          @Value("${minio.certificates-bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String store(String key, InputStream content, long size, String contentType) {
        try {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(content, size, -1)
                            .contentType(contentType)
                            .build()
            );
            return key;
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to store certificate in object storage", exception);
        }
    }

    @Override
    public byte[] get(String key) {
        try (InputStream input = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build()
        )) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to read certificate from object storage", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to delete certificate from object storage", exception);
        }
    }
}
