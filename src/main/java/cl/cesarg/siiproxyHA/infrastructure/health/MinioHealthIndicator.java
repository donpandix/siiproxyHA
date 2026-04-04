package cl.cesarg.siiproxyHA.infrastructure.health;

import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;

    public MinioHealthIndicator(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        try {
            // Quick connectivity check: attempt to list buckets by fetching an object that may not exist
            // Use a lightweight API call: statObject on a non-existing key in default bucket if configured.
            // If no bucket configured, fallback to calling server info via stream() (not available reliably).
            // We'll try a harmless call to check server availability.
            Optional.ofNullable(minioClient.listBuckets());
            return Health.up().withDetail("minio", "reachable").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("minio", e.getMessage()).build();
        }
    }
}
