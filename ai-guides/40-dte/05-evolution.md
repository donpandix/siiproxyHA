# 5. Estrategia de evolución para otros tipos de DTE

Principio: diseñar para extensión por tipo de documento sin permitir que la necesidad de soporte futuro contamine el core actual.

Cómo añadir nuevos tipos (procedimiento obligatorio):

1. Crear un nuevo `Processor` en `application.processors` (ej: `Factura61Processor`).
2. Implementar validaciones y transformaciones específicas en ese `Processor` o en clases auxiliares del módulo de extensión.
3. Reutilizar puertos del `core` (`StoragePort`, `DocumentoRepositoryPort`, `FolioPort`); si falta un contrato, añadir un puerto mínimo en `domain` con justificación en PR.
4. Añadir tests unitarios y de integración que repliquen el vertical slice del tipo 33 para el nuevo tipo.

Qué se espera reutilizar (debe):

- Puertos e interfaces del `domain` (storage, repository, queue).
- Infraestructura común: adaptadores a Postgres, MinIO y SQS.
- Mecanismos de trazabilidad y tablas `processing_history`.

Qué se espera extender o reemplazar (puede):

- Mappers XML↔domain por tipo documental.
- Reglas semánticas, XSD y generación de TED si difieren entre tipos.
- Plantillas de naming y paths en MinIO si el ciclo documental lo exige.

Reglas de gobernanza:

- Cualquier cambio a `domain` requiere justificación, tests y un plan de migración de datos si altera esquemas persistidos.
- Evitar introducir flags de runtime globales para soportar múltiples tipos; preferir registrar procesadores y resolución por tipo en tiempo de arranque.
