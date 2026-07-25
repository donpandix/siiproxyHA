package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.RutUtils;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort.CafFailureReason;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort.CafMaterial;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort.CafMaterialDescriptor;
import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort.CafMaterialUnavailableException;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.CafRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Opens a CAF private key only for a bounded infrastructure operation.
 */
@Component
public class CafPrivateKeyResolver {

    private final CafRepository repository;
    private final StoragePort storage;
    private final SecureCafXmlParser parser;

    public CafPrivateKeyResolver(
            CafRepository repository,
            StoragePort storage,
            SecureCafXmlParser parser
    ) {
        this.repository = repository;
        this.storage = storage;
        this.parser = parser;
    }

    /**
     * Revalidates the CAF and executes one operation with its RSA private key.
     */
    @Transactional(readOnly = true)
    public <T> T withPrivateKey(CafMaterial material, CafKeyOperation<T> operation)
            throws Exception {
        Objects.requireNonNull(material, "material is required");
        Objects.requireNonNull(operation, "operation is required");
        CafMaterialDescriptor descriptor = material.descriptor();

        Caf caf = repository.findById(descriptor.cafId())
                .filter(Caf::isActive)
                .orElseThrow(() -> unavailable(
                        CafFailureReason.NOT_FOUND,
                        "CAF private material is not available"
                ));
        validateDescriptor(caf, descriptor);

        byte[] authorizationXml = readAuthorization(caf);
        try {
            verifyStoredHash(caf, authorizationXml);
            SecureCafXmlParser.ParsedAuthorization parsed =
                    parser.parsePrivateMaterial(authorizationXml);
            if (parsed.privateKey() == null) {
                throw unavailable(
                        CafFailureReason.PRIVATE_KEY_UNAVAILABLE,
                        "CAF does not contain private signing material"
                );
            }
            if (!MessageDigest.isEqual(
                    material.publicCafXml(),
                    parsed.parsedCaf().publicCafXml()
            )) {
                throw unavailable(
                        CafFailureReason.INTEGRITY_FAILURE,
                        "CAF public material changed after selection"
                );
            }
            return operation.execute(parsed.privateKey());
        } finally {
            Arrays.fill(authorizationXml, (byte) 0);
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

    private void validateDescriptor(Caf caf, CafMaterialDescriptor descriptor) {
        if (caf.getTenant() == null
                || !descriptor.tenantId().equals(caf.getTenant().getId())
                || !descriptor.rutEmisor().equals(normalizeRut(caf.getRutEmisor()))
                || !descriptor.rutEmisor().equals(normalizeRut(caf.getTenant().getRutEmisor()))
                || !Objects.equals(descriptor.tipoDte(), caf.getTipoDte())
                || !Objects.equals(descriptor.puntoVenta(), caf.getPuntoVenta())
                || !Objects.equals(descriptor.folioDesde(), caf.getFolioDesde())
                || !Objects.equals(descriptor.folioHasta(), caf.getFolioHasta())
                || !Objects.equals(descriptor.authorizationDate(), caf.getFchAutorizacion())) {
            throw unavailable(
                    CafFailureReason.METADATA_MISMATCH,
                    "CAF metadata changed after selection"
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
                    "Unable to read CAF private material",
                    exception
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

    @FunctionalInterface
    public interface CafKeyOperation<T> {
        T execute(PrivateKey privateKey) throws Exception;
    }
}
