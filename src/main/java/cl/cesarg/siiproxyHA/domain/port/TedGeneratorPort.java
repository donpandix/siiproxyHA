package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates the SII electronic stamp without exposing CAF private material.
 */
public interface TedGeneratorPort {

    /**
     * Builds and signs one TED for an already assigned folio.
     */
    GeneratedTed generate(TedRequest request);

    record TedRequest(
            UUID tenantId,
            String emitterRut,
            int tipoDte,
            int puntoVenta,
            long folio,
            UUID assignedCafId,
            LocalDate emissionDate,
            String receiverRut,
            String receiverName,
            long totalAmount,
            String firstItem
    ) {

        public TedRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            emitterRut = RutUtils.normalizeAndValidate(emitterRut, "emitterRut");
            if (tipoDte <= 0) {
                throw new IllegalArgumentException("tipoDte must be positive");
            }
            if (puntoVenta <= 0) {
                throw new IllegalArgumentException("puntoVenta must be positive");
            }
            if (folio <= 0) {
                throw new IllegalArgumentException("folio must be positive");
            }
            Objects.requireNonNull(assignedCafId, "assignedCafId is required");
            Objects.requireNonNull(emissionDate, "emissionDate is required");
            receiverRut = RutUtils.normalizeAndValidate(receiverRut, "receiverRut");
            receiverName = requiredText(receiverName, "receiverName");
            if (totalAmount < 0) {
                throw new IllegalArgumentException("totalAmount must not be negative");
            }
            firstItem = requiredText(firstItem, "firstItem");
        }

        private static String requiredText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return value.trim();
        }
    }

    record GeneratedTed(
            byte[] tedXml,
            byte[] ddXml,
            LocalDateTime generatedAt,
            UUID cafId
    ) {

        public GeneratedTed {
            tedXml = requiredBytes(tedXml, "tedXml");
            ddXml = requiredBytes(ddXml, "ddXml");
            Objects.requireNonNull(generatedAt, "generatedAt is required");
            Objects.requireNonNull(cafId, "cafId is required");
        }

        @Override
        public byte[] tedXml() {
            return Arrays.copyOf(tedXml, tedXml.length);
        }

        @Override
        public byte[] ddXml() {
            return Arrays.copyOf(ddXml, ddXml.length);
        }

        private static byte[] requiredBytes(byte[] value, String field) {
            Objects.requireNonNull(value, field + " is required");
            if (value.length == 0) {
                throw new IllegalArgumentException(field + " must not be empty");
            }
            return Arrays.copyOf(value, value.length);
        }
    }
}
