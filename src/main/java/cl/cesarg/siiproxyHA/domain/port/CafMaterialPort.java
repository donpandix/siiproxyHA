package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves CAF metadata without exposing the CAF private signing key.
 */
public interface CafMaterialPort {

    /**
     * Resolves the CAF that authorizes the requested folio.
     */
    CafMaterialDescriptor requireCaf(CafMaterialSelector selector);

    record CafMaterialSelector(
            UUID tenantId,
            int tipoDte,
            int puntoVenta,
            long folio
    ) {

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
