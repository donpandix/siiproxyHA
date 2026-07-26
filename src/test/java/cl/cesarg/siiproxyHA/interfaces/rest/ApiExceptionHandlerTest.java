package cl.cesarg.siiproxyHA.interfaces.rest;

import cl.cesarg.siiproxyHA.application.exception.DocumentRegenerationConflictException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void identifiesDteFolioConflictWithoutExposingSqlDetails() {
        SQLException sqlException = new SQLException("duplicate key contains sensitive SQL");
        ConstraintViolationException constraintException = new ConstraintViolationException(
                "could not execute statement", sqlException, "uq_dte_tenant_tipo_folio");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "persistence failure", constraintException);

        var response = handler.conflict(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("error", "data_conflict");
        assertThat(response.getBody()).containsEntry("conflict", "dte_folio");
        assertThat(response.getBody()).containsEntry(
                "message", "A DTE already exists for tenant, tipoDte and folio");
        assertThat(response.getBody().toString()).doesNotContain("sensitive SQL");
    }

    @Test
    void mapsConcurrentRegenerationToConflict() {
        var response = handler.regenerationConflict(
                new DocumentRegenerationConflictException(
                        "Signed XML regeneration is already running"
                )
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry(
                "error",
                "regeneration_conflict"
        );
    }
}
