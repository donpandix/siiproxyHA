package cl.cesarg.siiproxyHA.domain.port;

public interface SiiStatusQueryPort {

    StatusResult query(StatusRequest request);

    record StatusRequest(
            String environment,
            String rutCompany,
            String dvCompany,
            long trackId,
            String token
    ) {}

    record StatusResult(
            int httpStatus,
            String trackId,
            String status,
            String glosa,
            String numeroAtencion,
            Integer informedCount,
            Integer acceptedCount,
            Integer rejectedCount,
            Integer repairCount,
            boolean authenticationRejected,
            byte[] rawResponse
    ) {}
}
