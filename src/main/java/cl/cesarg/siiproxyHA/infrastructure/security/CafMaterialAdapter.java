package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort;
import cl.cesarg.siiproxyHA.domain.port.CafParserPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves stored CAF artifacts and exposes only their public CAF block.
 */
@Component
public class CafMaterialAdapter implements CafMaterialPort {

    private final CafRepository repository;
    private final StoragePort storage;
    private final CafParserPort parser;

    public CafMaterialAdapter(
            CafRepository repository,
            StoragePort storage,
            CafParserPort parser
    ) {
        this.repository = repository;
        this.storage = storage;
        this.parser = parser;
    }

    @Override
    @Transactional(readOnly = true)
    public CafMaterial requireCaf(CafMaterialSelector selector) {
        Objects.requireNonNull(selector, "selector is required");
        Caf caf = selectCaf(selector);
        validateEntity(caf, selector);

        byte[] authorizationXml = readAuthorization(caf);
        try {
            verifyStoredHash(caf, authorizationXml);
            CafParserPort.ParsedCaf parsed = parser.parse(authorizationXml);
            validateParsedMaterial(caf, selector, parsed);

            CafMaterialDescriptor descriptor = new CafMaterialDescriptor(
                    caf.getId(),
                    selector.tenantId(),
                    parsed.rutEmisor(),
                    parsed.tipoDte(),
                    selector.puntoVenta(),
                    parsed.folioDesde(),
                    parsed.folioHasta(),
                    parsed.authorizationDate()
            );
            return new CafMaterial(descriptor, parsed.publicCafXml());
        } finally {
            Arrays.fill(authorizationXml, (byte) 0);
        }
    }

    private Caf selectCaf(CafMaterialSelector selector) {
        if (selector.assignedCafId() != null) {
            return repository.findById(selector.assignedCafId())
                    .filter(Caf::isActive)
                    .orElseThrow(() -> unavailable(
                            CafFailureReason.NOT_FOUND,
                            "Assigned CAF is not available"
                    ));
        }

        List<Caf> matches = repository
                .findByTenantIdAndTipoDteAndPuntoVentaAndActiveTrueOrderByCreatedAtAsc(
                        selector.tenantId(),
                        selector.tipoDte(),
                        selector.puntoVenta()
                )
                .stream()
                .filter(caf -> caf.getFolioDesde() != null
                        && caf.getFolioHasta() != null
                        && selector.folio() >= caf.getFolioDesde()
                        && selector.folio() <= caf.getFolioHasta())
                .toList();

        if (matches.isEmpty()) {
            throw unavailable(CafFailureReason.NOT_FOUND, "No CAF authorizes the requested folio");
        }
        if (matches.size() > 1) {
            throw unavailable(
                    CafFailureReason.AMBIGUOUS,
                    "More than one CAF authorizes the requested folio"
            );
        }
        return matches.getFirst();
    }

    private void validateEntity(Caf caf, CafMaterialSelector selector) {
        if (!caf.isActive()
                || caf.getTenant() == null
                || !selector.tenantId().equals(caf.getTenant().getId())
                || !Objects.equals(selector.tipoDte(), caf.getTipoDte())
                || !Objects.equals(selector.puntoVenta(), caf.getPuntoVenta())
                || caf.getFolioDesde() == null
                || caf.getFolioHasta() == null
                || selector.folio() < caf.getFolioDesde()
                || selector.folio() > caf.getFolioHasta()) {
            throw unavailable(
                    CafFailureReason.METADATA_MISMATCH,
                    "CAF metadata does not authorize the requested folio"
            );
        }
    }

    private byte[] readAuthorization(Caf caf) {
        try {
            byte[] bytes = storage.get(caf.getCafPath());
            if (bytes == null || bytes.length == 0) {
                throw unavailable(CafFailureReason.INVALID_XML, "Stored CAF is empty");
            }
            return bytes;
        } catch (CafMaterialUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CafMaterialUnavailableException(
                    CafFailureReason.STORAGE_UNAVAILABLE,
                    "Unable to read CAF material",
                    exception
            );
        }
    }

    private void verifyStoredHash(Caf caf, byte[] authorizationXml) {
        String expectedHash = caf.getCafSha256();
        if (expectedHash == null || expectedHash.length() != 64) {
            throw unavailable(
                    CafFailureReason.INTEGRITY_FAILURE,
                    "CAF checksum metadata is unavailable"
            );
        }

        try {
            byte[] expected = HexFormat.of().parseHex(expectedHash);
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(authorizationXml);
            try {
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw unavailable(
                            CafFailureReason.INTEGRITY_FAILURE,
                            "Stored CAF checksum does not match metadata"
                    );
                }
            } finally {
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(actual, (byte) 0);
            }
        } catch (CafMaterialUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CafMaterialUnavailableException(
                    CafFailureReason.INTEGRITY_FAILURE,
                    "Unable to verify CAF checksum",
                    exception
            );
        }
    }

    private void validateParsedMaterial(
            Caf caf,
            CafMaterialSelector selector,
            CafParserPort.ParsedCaf parsed
    ) {
        String tenantRut = normalizeRut(caf.getTenant().getRutEmisor());
        String entityRut = normalizeRut(caf.getRutEmisor());
        if (!tenantRut.equals(parsed.rutEmisor())
                || !entityRut.equals(parsed.rutEmisor())
                || !Objects.equals(caf.getTipoDte(), parsed.tipoDte())
                || !Objects.equals(caf.getFolioDesde(), parsed.folioDesde())
                || !Objects.equals(caf.getFolioHasta(), parsed.folioHasta())
                || !Objects.equals(caf.getFchAutorizacion(), parsed.authorizationDate())
                || !parsed.authorizes(selector.folio())) {
            throw unavailable(
                    CafFailureReason.METADATA_MISMATCH,
                    "Stored CAF content does not match tenant or metadata"
            );
        }
    }

    private String normalizeRut(String rut) {
        try {
            return RutUtils.normalizeAndValidate(rut, "rutEmisor");
        } catch (IllegalArgumentException exception) {
            throw unavailable(
                    CafFailureReason.METADATA_MISMATCH,
                    "CAF contains invalid issuer metadata"
            );
        }
    }

    private CafMaterialUnavailableException unavailable(
            CafFailureReason reason,
            String message
    ) {
        return new CafMaterialUnavailableException(reason, message);
    }
}
