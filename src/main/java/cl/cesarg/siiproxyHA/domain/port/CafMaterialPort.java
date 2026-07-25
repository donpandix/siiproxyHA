package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves CAF metadata without exposing the CAF private signing key.
 */
public interface CafMaterialPort {

    /**
     * Resolves the CAF that authorizes the requested folio.
     */
    CafMaterial requireCaf(CafMaterialSelector selector);

    enum CafFailureReason {
        NOT_FOUND,
        AMBIGUOUS,
        METADATA_MISMATCH,
        INTEGRITY_FAILURE,
        STORAGE_UNAVAILABLE,
        INVALID_XML,
        PRIVATE_KEY_UNAVAILABLE
    }

    class CafMaterialUnavailableException extends RuntimeException {

        private final CafFailureReason reason;

        public CafMaterialUnavailableException(CafFailureReason reason, String message) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public CafMaterialUnavailableException(
                CafFailureReason reason,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public CafFailureReason getReason() {
            return reason;
        }
    }

    record CafMaterialSelector(
            UUID tenantId,
            int tipoDte,
            int puntoVenta,
            long folio,
            UUID assignedCafId
    ) {

        public CafMaterialSelector(UUID tenantId, int tipoDte, int puntoVenta, long folio) {
            this(tenantId, tipoDte, puntoVenta, folio, null);
        }

        public CafMaterialSelector {
            Objects.requireNonNull(tenantId, "tenantId is required");
            if (tipoDte <= 0) {
                throw new IllegalArgumentException("tipoDte must be positive");
            }
            if (puntoVenta <= 0) {
                throw new IllegalArgumentException("puntoVenta must be positive");
            }
            if (folio <= 0) {
                throw new IllegalArgumentException("folio must be positive");
            }
        }
    }

    record CafMaterial(CafMaterialDescriptor descriptor, byte[] publicCafXml) {

        public CafMaterial {
            Objects.requireNonNull(descriptor, "descriptor is required");
            Objects.requireNonNull(publicCafXml, "publicCafXml is required");
            if (publicCafXml.length == 0) {
                throw new IllegalArgumentException("publicCafXml must not be empty");
            }
            publicCafXml = Arrays.copyOf(publicCafXml, publicCafXml.length);
        }

        @Override
        public byte[] publicCafXml() {
            return Arrays.copyOf(publicCafXml, publicCafXml.length);
        }
    }

    record CafMaterialDescriptor(
            UUID cafId,
            UUID tenantId,
            String rutEmisor,
            int tipoDte,
            int puntoVenta,
            long folioDesde,
            long folioHasta,
            LocalDate authorizationDate
    ) {

        public CafMaterialDescriptor {
            Objects.requireNonNull(cafId, "cafId is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            rutEmisor = RutUtils.normalizeAndValidate(rutEmisor, "rutEmisor");
            if (tipoDte <= 0) {
                throw new IllegalArgumentException("tipoDte must be positive");
            }
            if (puntoVenta <= 0) {
                throw new IllegalArgumentException("puntoVenta must be positive");
            }
            if (folioDesde <= 0 || folioHasta < folioDesde) {
                throw new IllegalArgumentException("CAF folio range is invalid");
            }
            Objects.requireNonNull(authorizationDate, "authorizationDate is required");
        }

        public boolean authorizes(long folio) {
            return folio >= folioDesde && folio <= folioHasta;
        }
    }
}
