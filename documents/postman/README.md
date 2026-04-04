# Postman — siiproxyHA

Importar los siguientes archivos en Postman:

- `documents/postman/siiproxyha-collection.json` (colección de llamadas)
- `documents/postman/siiproxyha-environment.json` (entorno local)

Pasos rápidos:

1. Arrancar infraestructura local: `docker compose up -d` (Postgres, MinIO, LocalStack si aplica).
2. Ejecutar la aplicación: `./mvnw spring-boot:run`.
3. Importar el environment y seleccionar `siiproxyHA Local`.
4. Importar la colección y ejecutar `POST Ingest DTE`.
   - La colección guardará el `documentId` (o `id`) en la variable `documentId` del environment si la respuesta contiene el campo.
5. Ejecutar `GET Document Status` y `GET Document XML (presigned)` usando la variable `{{documentId}}`.

Notas:
- `GET /api/v1/dte/{id}/status` y `GET /api/v1/dte/{id}/xml` deben estar implementados en la app para funcionar.
- El `POST` en la colección es idempotente si se reenvía con el mismo `documentId`.
