package cl.cesarg.siiproxyHA.domain.port;

import java.io.InputStream;

public interface CertificateStoragePort {

    String store(String key, InputStream content, long size, String contentType) throws Exception;

    byte[] get(String key) throws Exception;

    void delete(String key);
}
