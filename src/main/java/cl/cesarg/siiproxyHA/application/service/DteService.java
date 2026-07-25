package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.application.dto.DteXmlResponse;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.Dte;

public interface DteService {
    DocumentMetadata ingest(DteRequest request) throws Exception;

    DocumentMetadata store(Dte dte) throws Exception;

    DocumentMetadata getStatus(String documentId) throws Exception;

    DteXmlResponse getXml(String documentId, boolean presigned, int expiryMinutes) throws Exception;
}
