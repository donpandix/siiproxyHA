package cl.cesarg.siiproxyHA.interfaces.rest;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class TenantContextResolver {

    public UUID resolve(String tenantHeaderValue) {
        if (tenantHeaderValue == null || tenantHeaderValue.isBlank()) {
            throw new IllegalArgumentException("X-Tenant-Id header is required");
        }
        try {
            return UUID.fromString(tenantHeaderValue.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("X-Tenant-Id header must be a valid UUID");
        }
    }
}
