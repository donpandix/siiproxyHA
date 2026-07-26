# 5. Estrategia de testing

Capas de pruebas obligatorias:

- Unitarias: lógica de `core` y componentes pequeños. Mockear adaptadores.
- Integración: arranque parcial con contenedores (`docker-compose`) para Postgres y MinIO.
- Contract/API: tests que verifiquen compatibilidad con OpenAPI (pact o tests integrados).

Reglas:

- Cada nuevo endpoint debe incluir tests unitarios y un test de integración básico que ejecute el flujo principal.
- Los tests de integración deben ejecutarse en CI contra servicios dockerizados o emulados (MinIO, LocalStack).
- Evitar dependencias de red en unit tests.

Herramientas sugeridas:

- JUnit 5
- Mockito
- Testcontainers (alternativa a docker-compose en CI)
- Spring Boot Test

Observaciones:

- Mantener fixtures legibles en `src/test/resources`.
- Para pruebas de performance/contrato, documentar y ejecutar por separado.

## Vertical de integración DTE

`DteEmissionIntegrationTest` levanta PostgreSQL y MinIO con Testcontainers y
ejecuta el flujo HTTP completo: creación de CAF y PKCS#12 efímeros, asignación
de folio, TED/FRMT, firmas de `Documento` y `SetDTE`, validación integral,
persistencia de estados, almacenamiento, descarga, replay idempotente y
regeneración firmada del mismo artefacto sin consumir otro folio.

Ejecución focalizada:

```bash
./mvnw -q \
  -Dtest=cl.cesarg.siiproxyHA.application.service.DteEmissionIntegrationTest \
  test
```

La prueba requiere Docker y se omite cuando Docker no está disponible. No
realiza llamadas al ambiente de certificación ni a servicios productivos del
SII.

## Actualización parcial de empresa

`TenantServicePostgresIntegrationTest` verifica contra PostgreSQL que un `PUT`
parcial actualice los datos de resolución SII sin escribir `NULL` en los campos
obligatorios del tenant ni eliminar receptores cuando `receptores` se omite.
