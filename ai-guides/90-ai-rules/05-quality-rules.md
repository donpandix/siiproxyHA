# 5. Reglas de calidad (servicios, DTOs, entidades, testing)

Diseño de servicios y límites de responsabilidad:

- Una clase debe tener una sola responsabilidad clara (SRP). Si una clase necesita más de 3 dependencias, re-evaluar el diseño.
- Los servicios de application deben orquestar puertos y delegar la lógica compleja al `domain` o a adaptadores.

Reglas para DTOs, entidades y value objects:

- `DTO` (interfaces.rest): solo campos que forman parte del contrato HTTP; no incluir lógica ni anotaciones JPA.
- `Entity` (infrastructure.persistence): mapeos JPA y columnas DB; los mappers a `domain` deben ser explícitos y testados.
- `Value Object` (domain): inmutable, validado en construcción y con reglas de igualdad basadas en valores.

Adapters y clients:

- Los adapters deben implementar puertos definidos y deben ser intercambiables (no exponer tipos internos del proveedor en la API del puerto).
- Los clients HTTP o S3 no deben contener lógica de negocio; solo transformación y manejo de errores de transporte.

Logs, errores y validaciones:

- Validaciones de entrada sin estado deben hacerse en `interfaces` y mapear a objetos de dominio validados.
- Errores de negocio deben representarse con excepciones de dominio y traducirse a respuestas HTTP en una capa de manejo de excepciones.
- Logs deben ser estructurados; incluir `traceId` y no contener datos sensibles.

Testing (obligatorio):

- Unit tests para `domain` y `application` sin depender de infra.
- Integration tests que usen `docker-compose` o Testcontainers para validar flujo con Postgres y MinIO.
- Cada bugfix o feature que afecte la persistencia o storage debe añadir o actualizar tests de integración.

Medidas de calidad en CI:

- Ejecutar `mvn -DskipTests=false verify` y un paso de formateo/linters antes de merge.
