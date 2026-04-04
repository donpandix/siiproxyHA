package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;

public interface DteService {
    DocumentMetadata ingest(DteRequest request) throws Exception;

    DocumentMetadata getStatus(String documentId) throws Exception;

    cl.cesarg.siiproxyHA.application.dto.DteXmlResponse getXml(String documentId, boolean presigned, int expiryMinutes) throws Exception;
}
