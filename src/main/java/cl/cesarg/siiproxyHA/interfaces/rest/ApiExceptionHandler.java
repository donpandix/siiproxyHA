package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.exception.ResourceNotFoundException;
import cl.cesarg.siiproxyHA.application.exception.ObjectStorageException;
import cl.cesarg.siiproxyHA.application.exception.DocumentRegenerationConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "not_found",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_failed");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_request",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(DocumentRegenerationConflictException.class)
    public ResponseEntity<Map<String, String>> regenerationConflict(
            DocumentRegenerationConflictException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "regeneration_conflict",
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
                "error", "data_conflict",
                "conflict", details.category(),
                "message", details.message()
        ));
    }

    @ExceptionHandler(ObjectStorageException.class)
    public ResponseEntity<Map<String, String>> storageUnavailable(ObjectStorageException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "storage_unavailable",
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
            return new ConflictDetails("record", "The request conflicts with an existing record");
        }
        return switch (constraint) {
            case "uq_dte_tenant_tipo_folio" ->
                    new ConflictDetails("dte_folio", "A DTE already exists for tenant, tipoDte and folio");
            case "uq_receptor_tenant_rut" ->
                    new ConflictDetails("receptor_rut", "A receptor with the same RUT already exists for tenant");
            case "uq_dte_item_line" ->
                    new ConflictDetails("item_line", "DTE item line numbers must be unique");
            case "uq_dte_ref_line" ->
                    new ConflictDetails("reference_line", "DTE reference line numbers must be unique");
            case "uq_folio_assignment" ->
                    new ConflictDetails("folio_assignment", "The folio is already assigned for tenant and tipoDte");
            case "uq_folio_assignment_tenant_request" ->
                    new ConflictDetails("folio_request", "The folio request was already processed for tenant");
            case "uk_tenant_rut_hash" ->
                    new ConflictDetails("certificate", "The certificate is already registered for tenant and user");
            default -> new ConflictDetails("record", "The request conflicts with an existing record");
        };
    }

    private record ConflictDetails(String category, String message) {
    }
}
