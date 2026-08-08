package cl.cesarg.siiproxyHA.domain.port;

import java.time.LocalDate;

public interface SiiDteReconciliationPort {

    ReconciliationResult query(ReconciliationRequest request);

    record ReconciliationRequest(
            String environment,
            String rutCompany,
            String dvCompany,
            String rutReceiver,
            String dvReceiver,
            int documentType,
            long folio,
            LocalDate emissionDate,
            long total,
            byte[] signedXml,
            String token
    ) {
        public ReconciliationRequest {
            signedXml = signedXml == null ? null : signedXml.clone();
        }

        @Override
        public byte[] signedXml() {
            return signedXml == null ? null : signedXml.clone();
        }
    }

    record ReconciliationResult(
            int httpStatus,
            String headerStatus,
            Boolean received,
            String documentStatus,
            String glosa,
            Long trackId,
            String numeroAtencion,
            boolean authenticationRejected,
            byte[] rawResponse
    ) {}
}
