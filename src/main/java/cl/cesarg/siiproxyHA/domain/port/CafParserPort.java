package cl.cesarg.siiproxyHA.domain.port;

import cl.cesarg.siiproxyHA.domain.model.RutUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

/**
 * Parses an SII CAF authorization into safe metadata and public XML.
 */
public interface CafParserPort {

    /**
     * Parses and validates one CAF XML artifact.
     */
    ParsedCaf parse(byte[] authorizationXml);

    record ParsedCaf(
            String rutEmisor,
            int tipoDte,
            long folioDesde,
            long folioHasta,
            LocalDate authorizationDate,
            byte[] publicCafXml,
            boolean privateKeyAvailable
    ) {

        public ParsedCaf {
            rutEmisor = RutUtils.normalizeAndValidate(rutEmisor, "rutEmisor");
            if (tipoDte <= 0) {
                throw new IllegalArgumentException("tipoDte must be positive");
            }
            if (folioDesde <= 0 || folioHasta < folioDesde) {
                throw new IllegalArgumentException("CAF folio range is invalid");
            }
            Objects.requireNonNull(authorizationDate, "authorizationDate is required");
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

        public boolean authorizes(long folio) {
            return folio >= folioDesde && folio <= folioHasta;
        }
    }
}
