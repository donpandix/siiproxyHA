# 2. Workflow completo del DTE 33 (funcional y técnico)

Descripción: secuencia operativa que define pasos, responsables por capa y artefactos generados.

Flujo end-to-end (paso a paso) — responsabilidades entre capas:

1) Recepción de request de emisión (Interfaces)

- Entrada: `POST /api/v1/dte` con payload mínimo: `{ documentId?, emitterRUT, receiverRUT, items, totals, rawXml? }`.
- La `interfaces` debe: validar esquema HTTP, asignar `requestId`, aplicar validaciones sintácticas iniciales y mapear a un DTO de `application`.

2) Validación de negocio (Application / Domain)

- `application` llama a casos de uso en `domain` para validaciones: RUT válidos, totals, reglas obligatorias de tipo 33.
- Validaciones complejas (XSD, esquemas) se realizan en adaptadores especializados si se requiere parsing XML.

3) Asignación de folio (Application + Infrastructure)

- `application` solicita a `FolioPort` un folio disponible.
- `infrastructure` implementa `FolioPort` usando una tabla transaccional `folio_sequences` o un servicio coordinador que garantiza unicidad y atomicidad.
- Regla: la asignación de folio debe ser atómica y duradera; si falla la reserva, el proceso debe abortar y reportar error al invocador.

4) Uso de CAF y generación de TED (Infrastructure + Application)

- `application` invoca `CAFProviderPort` para obtener el CAF correspondiente al rango de folios.
- `infrastructure` lee CAF desde MinIO o provider configurado y lo entrega como objeto inmutable al `application`.
- `application` genera TED (Ticket Electrónico) combinando datos + folio + CAF.

5) Construcción de XML y firma (Infrastructure)

- `application` solicita a `XmlBuilderPort` la construcción del XML normalizado.
- `infrastructure` implementa `XmlBuilderPort` y `SignerPort`: genera XML conforme a XSD y solicita la firma digital usando `KmsPort` (LocalStack KMS o provider real).
- Resultado: XML firmado y checksum (SHA256) calculado.

6) Persistencia de metadatos (Infrastructure → Postgres)

- `application` persiste `DocumentMetadata` y estado inicial `CREATED` a través de `DocumentoRepositoryPort`.
- Regla: no almacenar blobs grandes en la BD; solo `objectKey`, `checksum`, `size` y metadatos.

7) Almacenamiento de artefactos (Infrastructure → MinIO)

- `infrastructure.storage` guarda el XML firmado bajo `objectKey` con patrón: `dte/{yyyy}/{MM}/{documentId}-{folio}.xml`.
- `infrastructure` debe exponer `presignedUrl` para descarga temporal cuando aplique.

8) Encolado para envío asíncrono (Infrastructure → SQS)

- `application` encola un mensaje a SQS con `documentId`, `folio`, `attempt:0` y metadatos mínimos.
- Consumidor asíncrono (worker) toma mensaje, intenta enviar a SII (no implementado en MVP) y actualiza `processing_history`.

9) Registro de track y estados (Infrastructure → Postgres)

- Cada transición relevante añade un registro en `processing_history` con `timestamp`, `state`, `actor` y `details`.

10) Consulta posterior (Interfaces)

- `GET /api/v1/dte/{id}/status` devuelve `DocumentStatus`, folio y links a artefactos.
- `GET /api/v1/dte/{id}/xml` devuelve un `presignedUrl` para descarga si el requester está autorizado.

Idempotencia y deduplicación (regla obligatoria):

- El ingest endpoint debe ser idempotente: si el `documentId` es repetido, el sistema debe devolver la misma entidad sin reasignar folio ni duplicar artefactos.
- Deduplicación alternativa: usar checksum del XML y tabla `ingest_dedup` con TTL para evitar re-procesar envíos duplicados.

Transacciones y consistencia:

- La persistencia del metadata y la creación del registro de folio deben ser atómicas en BD.
- El almacenamiento en MinIO es eventual: la secuencia recomendada es `persistir metadatos(state=PENDING_STORE) → store artifact → update metadata(state=STORED)`.

Tests y validación:

- Unit tests para `Processor` del tipo 33.
- Integration tests end-to-end usando `docker-compose` con Postgres y MinIO que cubran ingest → store → persistencia → consulta.
