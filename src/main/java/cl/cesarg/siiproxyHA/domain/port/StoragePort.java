package cl.cesarg.siiproxyHA.domain.port;

import java.io.InputStream;

public interface StoragePort {
    /**
     * Store content and return object key.
     */
    String store(String key, InputStream content, long size, String contentType) throws Exception;

    /**
     * Retrieve raw bytes of an object stored by key.
     */
    byte[] get(String key) throws Exception;

    /**
     * Generate a presigned URL for the object valid for given minutes.
     */
    String presignedUrl(String key, int minutes) throws Exception;
}
