package cl.cesarg.siiproxyHA.domain.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SiiAuthenticationPort {

    TokenLease acquireToken(
            String environment,
            UUID tenantId,
            String signerRut,
            UUID signingCredentialId
    );

    void invalidateToken(
            String environment,
            UUID tenantId,
            String signerRut,
            UUID signingCredentialId
    );

    record TokenLease(
            String value,
            String environment,
            UUID signingCredentialId,
            OffsetDateTime expiresAt
    ) {}
}
