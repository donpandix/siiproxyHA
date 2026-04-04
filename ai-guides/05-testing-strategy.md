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
