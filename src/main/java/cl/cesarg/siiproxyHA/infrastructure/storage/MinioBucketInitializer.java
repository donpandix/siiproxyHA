package cl.cesarg.siiproxyHA.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private final MinioClient client;
    private final Set<String> buckets;

    public MinioBucketInitializer(MinioClient client,
                                  @Value("${minio.bucket}") String dteBucket,
                                  @Value("${minio.certificates-bucket}") String certificatesBucket) {
        this.client = client;
        this.buckets = new LinkedHashSet<>();
        this.buckets.add(dteBucket);
        this.buckets.add(certificatesBucket);
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String bucket : buckets) {
            ensureBucketExists(bucket);
        }
    }

    private void ensureBucketExists(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("MinIO bucket name must not be blank");
        }

        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                createBucketIfStillMissing(bucket);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize MinIO bucket '" + bucket + "'", exception);
        }
    }

    private void createBucketIfStillMissing(String bucket) throws Exception {
        try {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (Exception creationException) {
            // Another application instance may have created it after the first check.
            boolean nowExists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!nowExists) {
                throw creationException;
            }
        }
    }
}
