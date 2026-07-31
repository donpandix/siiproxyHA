package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.SiiSubmissionStatus;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.SiiAuthenticationPort;
import cl.cesarg.siiproxyHA.domain.port.SiiStatusQueryPort;
import cl.cesarg.siiproxyHA.domain.port.SiiUploadPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteStatusEventRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiiSubmissionProcessorTest {

    private SiiSubmissionRepository submissions;
    private DteRepository dteRepository;
    private DteStatusEventRepository events;
    private StoragePort storage;
    private SiiAuthenticationPort authentication;
    private SiiUploadPort upload;
    private SiiStatusQueryPort statusQuery;
    private SiiSubmissionProcessor processor;
    private SiiSubmissionEntity submission;
    private Dte dte;
    private byte[] xml;

    @BeforeEach
    void setUp() throws Exception {
        submissions = mock(SiiSubmissionRepository.class);
        dteRepository = mock(DteRepository.class);
        events = mock(DteStatusEventRepository.class);
        storage = mock(StoragePort.class);
        authentication = mock(SiiAuthenticationPort.class);
        upload = mock(SiiUploadPort.class);
        statusQuery = mock(SiiStatusQueryPort.class);
        SiiProperties properties = new SiiProperties();
        properties.setMaxStatusQueries(5);
        processor = new SiiSubmissionProcessor(
                submissions,
                dteRepository,
                events,
                storage,
                authentication,
                upload,
                statusQuery,
                properties
        );

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor("76184688-4");
        dte = new Dte();
        dte.setId(UUID.randomUUID());
        dte.setTenant(tenant);
        dte.setRutEnvia("10438332-7");
        dte.setCreatedAt(Instant.now());
        dte.setUpdatedAt(Instant.now());

        xml = "<EnvioDTE/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        submission = new SiiSubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setDteId(dte.getId());
        submission.setDocumentId(dte.getId().toString());
        submission.setSigningCredentialId(UUID.randomUUID());
        submission.setEnvironment("CERTIFICATION");
        submission.setArtifactKey("dte/test.xml");
        submission.setArtifactSha256(sha256(xml));
        submission.setArtifactSizeBytes((long) xml.length);
        submission.setStatus(SiiSubmissionStatus.UPLOADING);
        submission.setAttemptCount(1);
        submission.setStatusQueryCount(0);
        submission.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        submission.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        submission.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(submissions.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(submissions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dteRepository.findWithTenantById(dte.getId())).thenReturn(Optional.of(dte));
        when(dteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.get("dte/test.xml")).thenReturn(xml);
        when(storage.store(any(), any(InputStream.class), anyLong(), eq("application/xml")))
                .thenReturn("sii-responses/response.xml");
        when(authentication.acquireToken(
                "CERTIFICATION",
                tenant.getId(),
                "10438332-7",
                submission.getSigningCredentialId()
        )).thenReturn(new SiiAuthenticationPort.TokenLease(
                "TOKEN123",
                "CERTIFICATION",
                submission.getSigningCredentialId(),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(55)
        ));
    }

    @Test
    void successfulUploadSchedulesTrackIdQueryWithoutBlockingCaller() {
        when(upload.upload(any())).thenReturn(new SiiUploadPort.UploadResult(
                200,
                "0",
                253515328L,
                null,
                "<RECEPCIONDTE/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
        ));

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.UPLOAD
        ));

        assertEquals(SiiSubmissionStatus.RECEIVED, submission.getStatus());
        assertEquals(253515328L, submission.getTrackId());
        assertEquals(253515328L, dte.getSiiTrackId());
        assertNotNull(submission.getNextAttemptAt());
        assertNotNull(dte.getEnviadoAt());
        verify(events).save(any());
    }

    @Test
    void rejectedUploadTokenIsInvalidatedAndRetriedSafely() {
        when(upload.upload(any())).thenReturn(new SiiUploadPort.UploadResult(
                200,
                "5",
                null,
                "No autenticado",
                "<RECEPCIONDTE/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
        ));

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.UPLOAD
        ));

        assertEquals(SiiSubmissionStatus.PENDING_UPLOAD, submission.getStatus());
        verify(authentication).invalidateToken(
                "CERTIFICATION",
                dte.getTenant().getId(),
                dte.getRutEnvia(),
                submission.getSigningCredentialId()
        );
    }

    @Test
    void processedTrackIdCompletesSubmission() {
        submission.setStatus(SiiSubmissionStatus.STATUS_QUERYING);
        submission.setTrackId(253515328L);
        submission.setStatusQueryCount(1);
        when(statusQuery.query(any())).thenReturn(new SiiStatusQueryPort.StatusResult(
                200,
                "253515328",
                "EPR",
                "Envio Procesado",
                "12345",
                1,
                1,
                0,
                0,
                false,
                "<RESPUESTA/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
        ));

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.STATUS_QUERY
        ));

        assertEquals(SiiSubmissionStatus.PROCESSED, submission.getStatus());
        assertEquals("EPR", dte.getSiiEstado());
        assertEquals("Envio Procesado", dte.getSiiGlosa());
        assertEquals("12345", submission.getNumeroAtencion());
        assertEquals(1, submission.getAcceptedCount());
        assertEquals(0, submission.getRejectedCount());
        assertNotNull(submission.getCompletedAt());
    }

    @Test
    void processedTrackIdWithRejectedDocumentEndsRejected() {
        submission.setStatus(SiiSubmissionStatus.STATUS_QUERYING);
        submission.setTrackId(253515328L);
        submission.setStatusQueryCount(1);
        when(statusQuery.query(any())).thenReturn(new SiiStatusQueryPort.StatusResult(
                200,
                "253515328",
                "EPR",
                "Envio Procesado",
                "12345",
                1,
                0,
                1,
                0,
                false,
                "<RESPUESTA/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
        ));

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.STATUS_QUERY
        ));

        assertEquals(SiiSubmissionStatus.REJECTED, submission.getStatus());
        assertEquals(1, submission.getRejectedCount());
        assertEquals(
                "SII processed the submission with rejected documents",
                submission.getLastError()
        );
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
