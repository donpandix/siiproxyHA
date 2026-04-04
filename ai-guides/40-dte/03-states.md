# 3. Estados del ciclo de vida del DTE 33

Entidades principales: `DocumentMetadata`, `processing_history`.

Estados (definidos y obligatorios):

- `RECEIVED`: el request fue aceptado por la API; validaciones iniciales pendientes.
- `VALIDATED`: validaciones de negocio pasadas.
- `FOLIO_ASSIGNED`: folio fiscal reservado y registrado.
- `TED_GENERATED`: TED creado (antes de firma).
- `SIGNED`: XML firmado y checksum calculado.
- `STORED`: artefactos (XML firmado) persistidos en MinIO y `objectKey` en BD.
- `ENQUEUED`: mensaje enviado a SQS para envío/otros procesos.
- `SENT`: (opcional en integraciones futuras) enviado al SII y confirmado.
- `FAILED_RECOVERABLE`: ocurrió un error con posibilidad de retry (ej: storage timeout).
- `FAILED_FATAL`: error no recuperable (ej: invalid CAF, folio no autorizado).

Transiciones válidas (ejemplos):

- `RECEIVED` → `VALIDATED` → `FOLIO_ASSIGNED` → `TED_GENERATED` → `SIGNED` → `STORED` → `ENQUEUED` → `SENT`
- En cualquier punto, ante errores transitorios: → `FAILED_RECOVERABLE` → (backoff/retry) → reintentar la transición.
- Ante errores irreversibles: → `FAILED_FATAL`.

Errores recuperables (debe manejarse con reintentos y backoff):

- Timeouts en S3/MinIO o SQS.
- Errores de red transitorios.
- Contenciones temporales en asignación de folios (reintentar con backoff corto).

Errores no recuperables (no debe reintentar automáticamente):

- CAF inválido o ausente para el rango de folios asignado.
- Documentos con estructura faltante o inconsistente definida por reglas de negocio críticas.

Reglas de trazabilidad (obligatorio):

- Cada transición debe crear un registro en `processing_history` con: `document_id`, `from_state`, `to_state`, `timestamp`, `actor`, `notes`.
- Mantener un campo `last_error` en `DocumentMetadata` con el tipo de error y conteo de intentos.

Regla de visibilidad:

- La API de consulta debe mapear estados internos a un conjunto público estable (ej: `PENDING`, `COMPLETED`, `FAILED`) para evitar exponer la complejidad interna.
