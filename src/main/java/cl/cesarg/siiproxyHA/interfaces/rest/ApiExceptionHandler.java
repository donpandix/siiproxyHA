package cl.cesarg.siiproxyHA.interfaces.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.cesarg.siiproxyHA.application.exception.DocumentRegenerationConflictException;
import cl.cesarg.siiproxyHA.application.exception.IdempotencyKeyReusedException;
import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.application.exception.UnsupportedDocumentTypeException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", exception.getMessage(),
                "message", "The requested resource was not found"
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "INVALID_REQUEST");
        body.put("message", "Request validation failed");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_REQUEST",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<Map<String, String>> unsupportedType(UnsupportedDocumentTypeException exception) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "code", "DOCUMENT_TYPE_NOT_SUPPORTED",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<Map<String, String>> idempotencyConflict(IdempotencyKeyReusedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "IDEMPOTENCY_KEY_REUSED",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(DocumentRegenerationConflictException.class)
    public ResponseEntity<Map<String, String>> regenerationConflict(
            DocumentRegenerationConflictException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "REGENERATION_CONFLICT",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException exception) {
        String constraint = findConstraintName(exception);
        ConflictDetails details = describeConflict(constraint);
        log.warn("Data integrity conflict. constraint={}, category={}",
                constraint == null ? "unknown" : constraint, details.category(), exception);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", details.category(),
                "message", details.message()
        ));
    }

    @ExceptionHandler(ObjectStorageException.class)
    public ResponseEntity<Map<String, String>> storageUnavailable(ObjectStorageException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "STORAGE_UNAVAILABLE",
                "message", exception.getMessage()
        ));
    }

    private String findConstraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private ConflictDetails describeConflict(String constraint) {
        if (constraint == null) {
            return new ConflictDetails("DATA_CONFLICT", "The request conflicts with an existing record");
        }
        return switch (constraint) {
            case "uq_dte_tenant_tipo_folio" ->
                    new ConflictDetails("DTE_FOLIO_CONFLICT", "A DTE already exists for tenant, tipoDte and folio");
            case "uq_receptor_tenant_rut" ->
                    new ConflictDetails("RECEPTOR_RUT_CONFLICT", "A receptor with the same RUT already exists for tenant");
            case "uq_dte_item_line" ->
                    new ConflictDetails("DTE_ITEM_LINE_CONFLICT", "DTE item line numbers must be unique");
            case "uq_dte_ref_line" ->
                    new ConflictDetails("DTE_REFERENCE_LINE_CONFLICT", "DTE reference line numbers must be unique");
            case "uq_folio_assignment" ->
                    new ConflictDetails("FOLIO_ASSIGNMENT_CONFLICT", "The folio is already assigned for tenant and tipoDte");
            case "uq_folio_assignment_tenant_request" ->
                    new ConflictDetails("FOLIO_REQUEST_CONFLICT", "The folio request was already processed for tenant");
            case "uk_tenant_rut_hash" ->
                    new ConflictDetails("CERTIFICATE_CONFLICT", "The certificate is already registered for tenant and user");
            case "uk_api_idempotency_tenant_key_operation" ->
                    new ConflictDetails("IDEMPOTENCY_KEY_REUSED", "The Idempotency-Key has already been used with a different request.");
            default -> new ConflictDetails("DATA_CONFLICT", "The request conflicts with an existing record");
        };
    }

    private record ConflictDetails(String category, String message) {
    }
}
