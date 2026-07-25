package cl.cesarg.siiproxyHA.infrastructure.security;

import cl.cesarg.siiproxyHA.domain.port.CafMaterialPort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates a deterministic DD block and signs it with the selected CAF key.
 */
@Component
public class TedGeneratorAdapter implements TedGeneratorPort {

    private static final DateTimeFormatter SII_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int TED_TEXT_MAX_LENGTH = 40;

    private final CafMaterialPort cafMaterialPort;
    private final CafPrivateKeyResolver privateKeyResolver;
    private final Clock clock;

    @Autowired
    public TedGeneratorAdapter(
            CafMaterialPort cafMaterialPort,
            CafPrivateKeyResolver privateKeyResolver,
            @Value("${dte.signing-zone:America/Santiago}") String signingZone
    ) {
        this(
                cafMaterialPort,
                privateKeyResolver,
                Clock.system(ZoneId.of(signingZone))
        );
    }

    TedGeneratorAdapter(
            CafMaterialPort cafMaterialPort,
            CafPrivateKeyResolver privateKeyResolver,
            Clock clock
    ) {
        this.cafMaterialPort = Objects.requireNonNull(cafMaterialPort);
        this.privateKeyResolver = Objects.requireNonNull(privateKeyResolver);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public GeneratedTed generate(TedRequest request) {
        Objects.requireNonNull(request, "request is required");
        CafMaterialPort.CafMaterial caf = cafMaterialPort.requireCaf(
                new CafMaterialPort.CafMaterialSelector(
                        request.tenantId(),
                        request.tipoDte(),
                        request.puntoVenta(),
                        request.folio(),
                        request.assignedCafId()
                )
        );
        if (!request.emitterRut().equals(caf.descriptor().rutEmisor())) {
            throw new TedGenerationException(
                    TedFailureReason.CAF_MISMATCH,
                    "CAF issuer does not match TED emitter"
            );
        }

        LocalDateTime generatedAt = LocalDateTime.now(clock).withNano(0);
        byte[] ddXml = buildDd(request, caf, generatedAt);
        byte[] signature = null;
        try {
            signature = privateKeyResolver.withPrivateKey(caf, privateKey -> {
                Signature signer = Signature.getInstance("SHA1withRSA");
                signer.initSign(privateKey);
                signer.update(ddXml);
                return signer.sign();
            });
            String frmt = Base64.getEncoder().encodeToString(signature);
            String tedXml = "<TED version=\"1.0\">"
                    + new String(ddXml, StandardCharsets.ISO_8859_1)
                    + "<FRMT algoritmo=\"SHA1withRSA\">"
                    + frmt
                    + "</FRMT></TED>";
            return new GeneratedTed(
                    encodeLatin1(tedXml),
                    ddXml,
                    generatedAt,
                    caf.descriptor().cafId()
            );
        } catch (TedGenerationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TedGenerationException(
                    TedFailureReason.SIGNING_FAILURE,
                    "Unable to sign TED",
                    exception
            );
        } finally {
            if (signature != null) {
                Arrays.fill(signature, (byte) 0);
            }
            Arrays.fill(ddXml, (byte) 0);
        }
    }

    private byte[] buildDd(
            TedRequest request,
            CafMaterialPort.CafMaterial caf,
            LocalDateTime generatedAt
    ) {
        String ddXml = "<DD>"
                + element("RE", request.emitterRut())
                + element("TD", Integer.toString(request.tipoDte()))
                + element("F", Long.toString(request.folio()))
                + element("FE", request.emissionDate().toString())
                + element("RR", request.receiverRut())
                + element("RSR", tedText(request.receiverName()))
                + element("MNT", Long.toString(request.totalAmount()))
                + element("IT1", tedText(request.firstItem()))
                + new String(caf.publicCafXml(), StandardCharsets.UTF_8)
                + element("TSTED", generatedAt.format(SII_TIMESTAMP))
                + "</DD>";
        return encodeLatin1(ddXml);
    }

    private String element(String name, String value) {
        return "<" + name + ">" + escapeXml(value) + "</" + name + ">";
    }

    private String tedText(String value) {
        String normalized = value.trim();
        return normalized.length() <= TED_TEXT_MAX_LENGTH
                ? normalized
                : normalized.substring(0, TED_TEXT_MAX_LENGTH);
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private byte[] encodeLatin1(String xml) {
        try {
            ByteBuffer encoded = StandardCharsets.ISO_8859_1
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(xml));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new TedGenerationException(
                    TedFailureReason.UNSUPPORTED_CHARACTER,
                    "TED contains characters outside ISO-8859-1",
                    exception
            );
        }
    }

    public enum TedFailureReason {
        CAF_MISMATCH,
        UNSUPPORTED_CHARACTER,
        SIGNING_FAILURE
    }

    public static class TedGenerationException extends RuntimeException {

        private final TedFailureReason reason;

        public TedGenerationException(TedFailureReason reason, String message) {
            super(message);
            this.reason = Objects.requireNonNull(reason);
        }

        public TedGenerationException(
                TedFailureReason reason,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.reason = Objects.requireNonNull(reason);
        }

        public TedFailureReason getReason() {
            return reason;
        }
    }
}
