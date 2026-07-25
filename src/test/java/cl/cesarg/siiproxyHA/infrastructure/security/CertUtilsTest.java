package cl.cesarg.siiproxyHA.infrastructure.security;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CertUtilsTest {

    @Test
    void extractsRutFromSerialNumberOidEvenWhenRfc2253UsesDerHex() {
        X500Principal principal = new X500Principal(
                "SERIALNUMBER=10438332-7,CN=GUIDO EMILIO REYES RIOS,T=PERSONA NATURAL,C=CL"
        );

        // Java represents SERIALNUMBER as 2.5.4.5=#130a... in RFC2253.
        assertEquals("10438332-7", CertUtils.extractRutFromPrincipal(principal));
    }

    @Test
    void returnsNullWhenSubjectDoesNotContainRut() {
        X500Principal principal = new X500Principal("CN=Usuario Sin Rut,C=CL");

        assertNull(CertUtils.extractRutFromPrincipal(principal));
    }
}
