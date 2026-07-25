package cl.cesarg.siiproxyHA.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RutUtilsTest {

    @Test
    void normalizesAndValidatesChileanRut() {
        assertEquals("10438332-7", RutUtils.normalizeAndValidate("10.438.332-7", "rutEnvia"));
        assertEquals("60803000-K", RutUtils.normalizeAndValidate("60.803.000-k", "rutReceptor"));
        assertTrue(RutUtils.isValid("76184688-4"));
    }

    @Test
    void rejectsInvalidCheckDigit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RutUtils.normalizeAndValidate("10438332-8", "rutEnvia")
        );
        assertEquals("rutEnvia is invalid", exception.getMessage());
    }
}
