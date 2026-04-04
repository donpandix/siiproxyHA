# 2. Modelo de extensión (reglas operativas)

Propósito: permitir agregar tipos de DTE nuevos sin romper el soporte para Factura 33.

Reglas normativas:

- El `core` debe exponer únicamente interfaces estables: `DocumentoRepositoryPort`, `DocumentoProcessor` y `StoragePort`.
- Los procesadores por tipo de DTE (ej.: `Factura33Processor`) deben implementarse fuera del `core`, en un paquete `application.processors` o en un módulo `dte-extensions`.
- Añadir un nuevo tipo DTE debe implicar: crear un nuevo `Processor` que implemente las operaciones `validate(document)`, `store(document)`, `enqueue(document)` y `finalize(document)`.
- El `core` solo define contratos y tipos comunes (DocumentId, DocumentMetadata, DocumentStatus). Las reglas específicas (campos obligatorios, validaciones XSD) deben residir en el procesador del tipo.

Qué se considera parte del `core` reutilizable (solo puede):

- Tipos de identidad y valor (`DocumentId`, `RUT`, `Checksum`).
- Estados y enums comunes (`DocumentStatus`).
- Interfaces/puertos para persistencia, storage y mensajería.

Qué debe ser específico por tipo DTE (debe):

- Validaciones semánticas y mapeos XML→domain.
- Políticas de negocio exclusivas (por ejemplo, reglas de cálculo de impuestos especiales).
- Plantillas y transformaciones de salida específicas del tipo.

Encapsulamiento de reglas variables:

- Las reglas variables deben exponerse como objetos de estrategia inyectables en los `Processor` de cada tipo; nunca como condicionales en servicios compartidos.

Pruebas obligatorias al agregar un tipo:

- Tests unitarios de `Processor` y tests de integración que simulen el flujo end-to-end con MinIO y Postgres.
