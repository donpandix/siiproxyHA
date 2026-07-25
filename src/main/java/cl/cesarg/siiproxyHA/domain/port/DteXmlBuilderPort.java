package cl.cesarg.siiproxyHA.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds an unsigned SII EnvioDTE artifact without exposing DOM types.
 */
public interface DteXmlBuilderPort {

    /**
     * Builds an EnvioDTE containing one Documento and its signed TED.
     */
    BuiltDteXml build(BuildRequest request);

    enum BuildFailureReason {
        INVALID_TED,
        DD_CHANGED,
        UNSUPPORTED_XML,
        SERIALIZATION_FAILURE
    }

    class DteXmlBuildException extends RuntimeException {

        private final BuildFailureReason reason;

        public DteXmlBuildException(BuildFailureReason reason, String message) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public DteXmlBuildException(
                BuildFailureReason reason,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.reason = Objects.requireNonNull(reason, "reason is required");
        }

        public BuildFailureReason getReason() {
            return reason;
        }
    }

    record BuildRequest(
            UUID dteId,
            IssuerData issuer,
            ReceiverData receiver,
            DocumentData document,
            List<ItemData> items,
            List<ReferenceData> references,
            TedGeneratorPort.GeneratedTed ted
    ) {

        public BuildRequest {
            Objects.requireNonNull(dteId, "dteId is required");
            Objects.requireNonNull(issuer, "issuer is required");
            Objects.requireNonNull(receiver, "receiver is required");
            Objects.requireNonNull(document, "document is required");
            Objects.requireNonNull(items, "items is required");
            items = List.copyOf(items);
            Objects.requireNonNull(references, "references is required");
            references = List.copyOf(references);
            Objects.requireNonNull(ted, "ted is required");
        }
    }

    record IssuerData(
            String rutEmisor,
            String rutEnvia,
            String razonSocial,
            String giro,
            String acteco,
            String direccion,
            String comuna,
            LocalDate resolutionDate,
            int resolutionNumber
    ) {

        public IssuerData {
            rutEmisor = requiredText(rutEmisor, "rutEmisor");
            rutEnvia = nullableText(rutEnvia);
            razonSocial = nullableText(razonSocial);
            giro = nullableText(giro);
            acteco = nullableText(acteco);
            direccion = nullableText(direccion);
            comuna = nullableText(comuna);
            Objects.requireNonNull(resolutionDate, "resolutionDate is required");
            if (resolutionNumber < 0) {
                throw new IllegalArgumentException("resolutionNumber must not be negative");
            }
        }
    }

    record ReceiverData(
            String rut,
            String razonSocial,
            String giro,
            String direccion,
            String comuna
    ) {

        public ReceiverData {
            rut = requiredText(rut, "receiver.rut");
            razonSocial = nullableText(razonSocial);
            giro = nullableText(giro);
            direccion = nullableText(direccion);
            comuna = nullableText(comuna);
        }
    }

    record DocumentData(
            int tipoDte,
            long folio,
            LocalDate emissionDate,
            Long netAmount,
            BigDecimal vatRate,
            Long vatAmount,
            long totalAmount
    ) {

        public DocumentData {
            if (tipoDte <= 0) {
                throw new IllegalArgumentException("tipoDte must be positive");
            }
            if (folio <= 0) {
                throw new IllegalArgumentException("folio must be positive");
            }
            Objects.requireNonNull(emissionDate, "emissionDate is required");
            if (totalAmount < 0) {
                throw new IllegalArgumentException("totalAmount must not be negative");
            }
        }
    }

    record ItemData(
            Integer lineNumber,
            String name,
            String description,
            Double quantity,
            Double unitPrice,
            Long amount
    ) {

        public ItemData {
            name = nullableText(name);
            description = nullableText(description);
        }
    }

    record ReferenceData(
            Integer lineNumber,
            String documentType,
            String folio,
            LocalDate date,
            String code,
            String reason
    ) {

        public ReferenceData {
            documentType = nullableText(documentType);
            folio = nullableText(folio);
            code = nullableText(code);
            reason = nullableText(reason);
        }
    }

    record BuiltDteXml(
            byte[] xml,
            String documentoId,
            String setDteId,
            String encoding
    ) {

        public BuiltDteXml {
            Objects.requireNonNull(xml, "xml is required");
            if (xml.length == 0) {
                throw new IllegalArgumentException("xml must not be empty");
            }
            xml = Arrays.copyOf(xml, xml.length);
            documentoId = requiredText(documentoId, "documentoId");
            setDteId = requiredText(setDteId, "setDteId");
            encoding = requiredText(encoding, "encoding");
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String nullableText(String value) {
        return value == null ? "" : value.trim();
    }
}
