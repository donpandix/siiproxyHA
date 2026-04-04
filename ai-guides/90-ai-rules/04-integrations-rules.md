# 4. Reglas para integraciones (Postgres, MinIO, LocalStack)

Objetivo: definir responsabilidades claras y límites de cada integración.

PostgreSQL (persistencia transaccional y trazabilidad):

- PostgreSQL debe usarse para datos relacionales y trazabilidad de procesos (`document_metadata`, `processing_history`, `status_updates`).
- Las transacciones que incluyan cambios en la BD deben ser consistentes; cualquier operación que combine almacenamiento en MinIO debe ser transaccional eventual: primero persistir metadatos, luego almacenar archivo, luego actualizar referencial si aplica.
- No almacenar blobs grandes (XML/PDF) en la BD.

MinIO (almacenamiento de artefactos):

- MinIO debe almacenar todos los archivos: XML originales, archivos firmados, CAFs y PDFs generados.
- Cada objeto en MinIO debe tener un `objectKey` con prefijo por año/mes y `documentId` para trazabilidad.
- No implementar lógica de negocio en el adaptador de storage; el adaptador solo guarda/recupera/borra y expone presigned URLs.

LocalStack SQS (colas internas):

- SQS debe usarse para procesamiento asíncrono: firma, reintentos, reenvíos a SII.
- Mensajes deben ser idempotentes (incluir `documentId` y `attempt`), y el consumidor debe manejar duplicados.

LocalStack SSM (configuración simulada):

- SSM se puede usar en desarrollo para guardar parámetros no secretos (endpoints, feature flags), pero las credenciales sensibles deben usarse desde variables de entorno.

LocalStack KMS (criptografía):

- KMS solo debe usarse como abstracción para operaciones de firma/descifrado; las llaves privadas no deben estar en el repositorio.

Reglas operativas y de seguridad:

- Las adaptaciones a endpoints custom (MinIO, LocalStack) deben soportar configuración por `S3_ENDPOINT` y `AWS_REGION`.
- No escribir credenciales en código; los adaptadores deben leer variables de entorno o providers seguros.
- Cualquier operación de escritura crítica debe registrar un evento en `processing_history` en BD para auditoría.
