package cl.cesarg.siiproxyHA.infrastructure.sii;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "sii")
public class SiiProperties {

    private String environment = "CERTIFICATION";
    private boolean productionEnabled;
    private boolean workerEnabled = true;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration tokenTtl = Duration.ofMinutes(55);
    private Duration workerDelay = Duration.ofSeconds(2);
    private Duration claimLease = Duration.ofMinutes(5);
    private int maxUploadAttempts = 5;
    private Duration uploadRetryInitialDelay = Duration.ofSeconds(30);
    private Duration uploadRetryMaxDelay = Duration.ofMinutes(5);
    private int maxReconciliationAttempts = 5;
    private Duration reconciliationInitialDelay = Duration.ofMinutes(2);
    private Duration reconciliationMaxDelay = Duration.ofMinutes(30);
    private int maxStatusQueries = 30;
    private Endpoints certification = new Endpoints();
    private Endpoints production = new Endpoints();

    @PostConstruct
    void validate() {
        String normalized = normalizedEnvironment();
        if (!"CERTIFICATION".equals(normalized) && !"PRODUCTION".equals(normalized)) {
            throw new IllegalStateException("SII environment must be CERTIFICATION or PRODUCTION");
        }
        if ("PRODUCTION".equals(normalized) && !productionEnabled) {
            throw new IllegalStateException("SII production environment requires sii.production-enabled=true");
        }
        endpoints(normalized).validate(normalized);
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout.isNegative() || requestTimeout.isZero()
                || tokenTtl.isNegative() || tokenTtl.isZero()
                || workerDelay.isNegative() || workerDelay.isZero()
                || claimLease.isNegative() || claimLease.isZero()
                || uploadRetryInitialDelay.isNegative() || uploadRetryInitialDelay.isZero()
                || uploadRetryMaxDelay.isNegative() || uploadRetryMaxDelay.isZero()
                || reconciliationInitialDelay.isNegative() || reconciliationInitialDelay.isZero()
                || reconciliationMaxDelay.isNegative() || reconciliationMaxDelay.isZero()) {
            throw new IllegalStateException("SII durations must be positive");
        }
        if (maxStatusQueries < 1 || maxUploadAttempts < 1 || maxReconciliationAttempts < 1) {
            throw new IllegalStateException("SII maximum attempt counts must be positive");
        }
        if (uploadRetryMaxDelay.compareTo(uploadRetryInitialDelay) < 0
                || reconciliationMaxDelay.compareTo(reconciliationInitialDelay) < 0) {
            throw new IllegalStateException("SII maximum retry delays must not be shorter than initial delays");
        }
    }

    public String normalizedEnvironment() {
        return environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
    }

    public Endpoints activeEndpoints() {
        return endpoints(normalizedEnvironment());
    }

    public Endpoints endpoints(String requestedEnvironment) {
        String normalized = requestedEnvironment == null
                ? ""
                : requestedEnvironment.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CERTIFICATION" -> certification;
            case "PRODUCTION" -> {
                if (!productionEnabled) {
                    throw new IllegalStateException("SII production access is disabled");
                }
                yield production;
            }
            default -> throw new IllegalArgumentException("Unknown SII environment");
        };
    }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public boolean isProductionEnabled() { return productionEnabled; }
    public void setProductionEnabled(boolean productionEnabled) { this.productionEnabled = productionEnabled; }
    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public Duration getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
    public Duration getWorkerDelay() { return workerDelay; }
    public void setWorkerDelay(Duration workerDelay) { this.workerDelay = workerDelay; }
    public Duration getClaimLease() { return claimLease; }
    public void setClaimLease(Duration claimLease) { this.claimLease = claimLease; }
    public int getMaxUploadAttempts() { return maxUploadAttempts; }
    public void setMaxUploadAttempts(int maxUploadAttempts) { this.maxUploadAttempts = maxUploadAttempts; }
    public Duration getUploadRetryInitialDelay() { return uploadRetryInitialDelay; }
    public void setUploadRetryInitialDelay(Duration uploadRetryInitialDelay) { this.uploadRetryInitialDelay = uploadRetryInitialDelay; }
    public Duration getUploadRetryMaxDelay() { return uploadRetryMaxDelay; }
    public void setUploadRetryMaxDelay(Duration uploadRetryMaxDelay) { this.uploadRetryMaxDelay = uploadRetryMaxDelay; }
    public int getMaxReconciliationAttempts() { return maxReconciliationAttempts; }
    public void setMaxReconciliationAttempts(int maxReconciliationAttempts) { this.maxReconciliationAttempts = maxReconciliationAttempts; }
    public Duration getReconciliationInitialDelay() { return reconciliationInitialDelay; }
    public void setReconciliationInitialDelay(Duration reconciliationInitialDelay) { this.reconciliationInitialDelay = reconciliationInitialDelay; }
    public Duration getReconciliationMaxDelay() { return reconciliationMaxDelay; }
    public void setReconciliationMaxDelay(Duration reconciliationMaxDelay) { this.reconciliationMaxDelay = reconciliationMaxDelay; }
    public int getMaxStatusQueries() { return maxStatusQueries; }
    public void setMaxStatusQueries(int maxStatusQueries) { this.maxStatusQueries = maxStatusQueries; }
    public Endpoints getCertification() { return certification; }
    public void setCertification(Endpoints certification) { this.certification = certification; }
    public Endpoints getProduction() { return production; }
    public void setProduction(Endpoints production) { this.production = production; }

    public static class Endpoints {
        private URI seedUrl;
        private URI tokenUrl;
        private URI uploadUrl;
        private URI statusUrl;
        private URI dteStatusUrl;

        void validate(String environment) {
            validateHttps(seedUrl, environment + " seed-url");
            validateHttps(tokenUrl, environment + " token-url");
            validateHttps(uploadUrl, environment + " upload-url");
            validateHttps(statusUrl, environment + " status-url");
            validateHttps(dteStatusUrl, environment + " dte-status-url");
        }

        private void validateHttps(URI uri, String name) {
            if (uri == null
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || !uri.getHost().endsWith(".sii.cl")) {
                throw new IllegalStateException(name + " must be an HTTPS sii.cl endpoint");
            }
        }

        public URI getSeedUrl() { return seedUrl; }
        public void setSeedUrl(URI seedUrl) { this.seedUrl = seedUrl; }
        public URI getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(URI tokenUrl) { this.tokenUrl = tokenUrl; }
        public URI getUploadUrl() { return uploadUrl; }
        public void setUploadUrl(URI uploadUrl) { this.uploadUrl = uploadUrl; }
        public URI getStatusUrl() { return statusUrl; }
        public void setStatusUrl(URI statusUrl) { this.statusUrl = statusUrl; }
        public URI getDteStatusUrl() { return dteStatusUrl; }
        public void setDteStatusUrl(URI dteStatusUrl) { this.dteStatusUrl = dteStatusUrl; }
    }
}
