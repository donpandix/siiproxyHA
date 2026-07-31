package cl.cesarg.siiproxyHA.infrastructure.sii;

import cl.cesarg.siiproxyHA.domain.port.SiiUploadPort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class SiiUploadClient implements SiiUploadPort {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.ISO_8859_1);
    private final HttpClient httpClient;
    private final SiiProperties properties;

    public SiiUploadClient(HttpClient httpClient, SiiProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public UploadResult upload(UploadRequest request) {
        requireToken(request.token());
        String boundary = "----siiproxyHA-" + UUID.randomUUID();
        byte[] body = multipart(request, boundary);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(
                            properties.endpoints(request.environment()).getUploadUrl()
                    )
                    .timeout(properties.getRequestTimeout())
                    .header("User-Agent", "siiproxyHA/1.0")
                    .header("Cookie", "TOKEN=" + request.token())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            return parse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SiiTransportException(
                    "SII upload was interrupted; reception is unknown",
                    true,
                    exception
            );
        } catch (java.io.IOException exception) {
            throw new SiiTransportException(
                    "SII upload transport failed; reception is unknown",
                    true,
                    exception
            );
        }
    }

    private UploadResult parse(int httpStatus, byte[] response) {
        try {
            Document document = SiiXmlSupport.parse(response);
            String status = SiiXmlSupport.firstText(document, "STATUS");
            if (status == null) {
                status = SiiXmlSupport.firstText(document, "ESTADO");
            }
            String trackText = SiiXmlSupport.firstText(document, "TRACKID");
            Long trackId = trackText == null || trackText.isBlank()
                    ? null
                    : Long.valueOf(trackText.trim());
            String reason = SiiXmlSupport.firstText(document, "RAZON");
            if (reason == null) {
                reason = SiiXmlSupport.firstText(document, "DETAIL");
            }
            return new UploadResult(httpStatus, trim(status), trackId, trim(reason), response);
        } catch (RuntimeException exception) {
            return new UploadResult(
                    httpStatus,
                    null,
                    null,
                    "SII upload response could not be parsed",
                    response
            );
        }
    }

    private byte[] multipart(UploadRequest request, String boundary) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            field(output, boundary, "rutSender", request.rutSender());
            field(output, boundary, "dvSender", request.dvSender());
            field(output, boundary, "rutCompany", request.rutCompany());
            field(output, boundary, "dvCompany", request.dvCompany());
            output.write(("--" + boundary).getBytes(StandardCharsets.ISO_8859_1));
            output.write(CRLF);
            output.write((
                    "Content-Disposition: form-data; name=\"archivo\"; filename=\""
                            + safeFilename(request.filename()) + "\""
            ).getBytes(StandardCharsets.ISO_8859_1));
            output.write(CRLF);
            output.write("Content-Type: text/xml".getBytes(StandardCharsets.ISO_8859_1));
            output.write(CRLF);
            output.write(CRLF);
            output.write(request.xml());
            output.write(CRLF);
            output.write(("--" + boundary + "--").getBytes(StandardCharsets.ISO_8859_1));
            output.write(CRLF);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to construct SII multipart upload", exception);
        }
    }

    private void field(ByteArrayOutputStream output, String boundary, String name, String value)
            throws java.io.IOException {
        if (value == null || !value.matches("[0-9Kk]{1,12}")) {
            throw new IllegalArgumentException("Invalid SII multipart RUT field " + name);
        }
        output.write(("--" + boundary).getBytes(StandardCharsets.ISO_8859_1));
        output.write(CRLF);
        output.write((
                "Content-Disposition: form-data; name=\"" + name + "\""
        ).getBytes(StandardCharsets.ISO_8859_1));
        output.write(CRLF);
        output.write(CRLF);
        output.write(value.getBytes(StandardCharsets.ISO_8859_1));
        output.write(CRLF);
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "envio.xml" : filename;
        if (!value.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("Invalid SII upload filename");
        }
        return value;
    }

    private void requireToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9]{1,64}")) {
            throw new IllegalArgumentException("Invalid SII token");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
