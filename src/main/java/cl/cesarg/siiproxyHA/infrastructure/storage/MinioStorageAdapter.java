package cl.cesarg.siiproxyHA.infrastructure.storage;

import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioStorageAdapter implements StoragePort {

    private final MinioClient client;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioStorageAdapter(MinioClient client) {
        this.client = client;
    }

    @Override
    public String store(String key, InputStream content, long size, String contentType) throws Exception {
        // Ensure bucket exists is left to ops; here we store directly
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(content, size, -1)
                        .contentType(contentType)
                        .build()
        );
        return key;
    }

    @Override
    public byte[] get(String key) throws Exception {
        try (InputStream in = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build()
        )) {
            return in.readAllBytes();
        }
    }

    @Override
    public String presignedUrl(String key, int minutes) throws Exception {
        // expiry in seconds
        int expiry = Math.max(1, minutes) * 60;
        return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(key)
                        .expiry(expiry)
                        .build()
        );
    }
}
