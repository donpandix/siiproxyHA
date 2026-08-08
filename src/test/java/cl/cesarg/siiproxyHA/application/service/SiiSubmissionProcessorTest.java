package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.SiiSubmissionStatus;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.SiiAuthenticationPort;
import cl.cesarg.siiproxyHA.domain.port.SiiDteReconciliationPort;
import cl.cesarg.siiproxyHA.domain.port.SiiStatusQueryPort;
import cl.cesarg.siiproxyHA.domain.port.SiiUploadPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteStatusEventRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionEntity;
import cl.cesarg.siiproxyHA.infrastructure.persistence.SiiSubmissionRepository;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiProperties;
import cl.cesarg.siiproxyHA.infrastructure.sii.SiiTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
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
    private SiiDteReconciliationPort reconciliation;
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
        reconciliation = mock(SiiDteReconciliationPort.class);
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
                reconciliation,
                properties
        );

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor("76184688-4");
        dte = new Dte();
        dte.setId(UUID.randomUUID());
        dte.setTenant(tenant);
        dte.setRutEnvia("10438332-7");
        dte.setRutRecep("60803000-K");
        dte.setTipoDte(33);
        dte.setFolio(189L);
        dte.setFchEmis(LocalDate.of(2026, 8, 7));
        dte.setMntTotal(1190L);
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
        submission.setReconciliationCount(0);
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

    @ParameterizedTest
    @CsvSource({"1,30", "2,60", "3,120", "4,240"})
    void safeUploadRetryUsesExponentialWait(int attempt, long expectedSeconds) {
        submission.setAttemptCount(attempt);
        when(upload.upload(any())).thenReturn(new SiiUploadPort.UploadResult(
                200, "5", null, "No autenticado", new byte[0]
        ));
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.UPLOAD
        ));

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);
        assertEquals(SiiSubmissionStatus.PENDING_UPLOAD, submission.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(
                submission.getNextAttemptAt().isBefore(before.plusSeconds(expectedSeconds))
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                submission.getNextAttemptAt().isAfter(after.plusSeconds(expectedSeconds))
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

    @Test
    void ambiguousTransportFailureSchedulesReconciliationWithoutBlindUploadRetry() {
        when(upload.upload(any())).thenThrow(new SiiTransportException(
                "timeout after upload started",
                true,
                new java.io.IOException("timeout")
        ));

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.UPLOAD
        ));

        assertEquals(SiiSubmissionStatus.OUTCOME_UNKNOWN, submission.getStatus());
        assertEquals("TRANSPORT_AMBIGUOUS", submission.getFailureClass());
        assertNotNull(submission.getOutcomeUnknownAt());
        assertNotNull(submission.getNextAttemptAt());
    }

    @Test
    void safeFailuresStopAfterFiveTotalUploadAttempts() {
        submission.setAttemptCount(5);
        when(upload.upload(any())).thenThrow(new SiiTransportException(
                "connection failed before request was sent",
                false,
                new java.io.IOException("connection refused")
        ));

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.UPLOAD
        ));

        assertEquals(SiiSubmissionStatus.FAILED_RECOVERABLE, submission.getStatus());
        assertNotNull(submission.getCompletedAt());
    }

    @Test
    void reconciliationWithTrackIdContinuesNormalStatusPolling() {
        submission.setStatus(SiiSubmissionStatus.RECONCILING);
        submission.setReconciliationCount(1);
        when(reconciliation.query(any())).thenReturn(
                new SiiDteReconciliationPort.ReconciliationResult(
                        200, "0", true, "DOK", "Documento recibido",
                        253772832L, "123", false,
                        "<RESPUESTA/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
                )
        );

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.RECONCILIATION
        ));

        assertEquals(SiiSubmissionStatus.RECEIVED, submission.getStatus());
        assertEquals(253772832L, submission.getTrackId());
        assertNotNull(submission.getReconciledAt());
        assertEquals(253772832L, dte.getSiiTrackId());
    }

    @Test
    void twoExplicitNotReceivedAnswersAllowExactArtifactRetry() {
        submission.setStatus(SiiSubmissionStatus.RECONCILING);
        submission.setReconciliationCount(2);
        when(reconciliation.query(any())).thenReturn(
                new SiiDteReconciliationPort.ReconciliationResult(
                        200, "0", false, "FAU", "Documento no recibido",
                        null, "123", false,
                        "<RESPUESTA/>".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
                )
        );

        processor.process(new SiiSubmissionClaimService.Claim(
                submission.getId(),
                SiiSubmissionClaimService.Operation.RECONCILIATION
        ));

        assertEquals(SiiSubmissionStatus.PENDING_UPLOAD, submission.getStatus());
        assertEquals("CONFIRMED_NOT_RECEIVED", submission.getFailureClass());
        assertNotNull(submission.getReconciledAt());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
