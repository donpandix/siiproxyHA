# 8. Lineamientos específicos del dominio DTE

Objetivo: definir reglas y artefactos mínimos para manejar DTE tipo 33 y extender a otros tipos.

Modelado y datos:

- Entidades primarias: `DocumentoFiscal` (metadatos), `Emisor`, `Receptor`, `ArchivoXml` (referencia a S3), `EstadoProceso`.
- Guardar metadatos en Postgres; referencias a archivos XML/PDF en MinIO por `objectKey`.

Flujos principales (Factura 33):

1. Recepción del DTE (XML) → validación sintáctica y semántica básica.
2. Almacenamiento del XML en MinIO y persistencia de metadatos en Postgres dentro de una transacción eventual.
3. Encolar mensaje para procesamiento asíncrono (firma, envío al SII) usando SQS.
4. Actualizar estado y notificar resultados (webhook o polling según integración).

Validaciones y reglas de negocio:

- Validar estructura XML contra XSD cuando sea posible.
- Checksum de archivos y versión del documento en BD para trazabilidad.
- Idempotencia: operaciones de ingestión deben ser idempotentes (usar `documentId` o checksum deduplicador).

Seguridad y firma:

- Mantener claves privadas fuera del repositorio; usar KMS (LocalStack) en desarrollo.
