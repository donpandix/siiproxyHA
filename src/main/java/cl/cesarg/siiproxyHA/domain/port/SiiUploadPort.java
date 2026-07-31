package cl.cesarg.siiproxyHA.domain.port;

public interface SiiUploadPort {

    UploadResult upload(UploadRequest request);

    record UploadRequest(
            String environment,
            byte[] xml,
            String token,
            String rutSender,
            String dvSender,
            String rutCompany,
            String dvCompany,
            String filename
    ) {}

    record UploadResult(
            int httpStatus,
            String status,
            Long trackId,
            String reason,
            byte[] rawResponse
    ) {
        public boolean received() {
            return httpStatus >= 200
                    && httpStatus < 300
                    && "0".equals(status)
                    && trackId != null;
        }
    }
}
