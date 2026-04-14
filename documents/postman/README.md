Postman collection and quick curl examples

Import the collection at documents/postman/siiproxyha-collection.json into Postman.

Environment variables available in documents/postman/siiproxyha-environment.json:
- `baseUrl` (e.g. http://localhost:8080)
- `tenantId`, `receptorId`, `cafId`

Quick curl examples:

# Tenants
Create:
```
curl -X POST -H "Content-Type: application/json" -d '{"tenantCode":"acme","rutEmisor":"76184688-4","razonSocial":"ACME"}' {{baseUrl}}/api/v1/tenants
```

List:
```
curl {{baseUrl}}/api/v1/tenants
```

# Receptores
Create (for tenant):
```
curl -X POST -H "Content-Type: application/json" -d '{"rutReceptor":"22222222-2","razonSocial":"Cliente Uno"}' {{baseUrl}}/api/v1/tenants/<TENANT_ID>/receptores
```

List:
```
curl {{baseUrl}}/api/v1/tenants/<TENANT_ID>/receptores
```

# CAF
Upload:
```
curl -v -F "tenantId=<TENANT_ID>" -F "file=@documents/samples/FoliosSII_33.xml" {{baseUrl}}/api/v1/caf
```

Download:
```
curl -L -o caf.xml {{baseUrl}}/api/v1/caf/<CAF_ID>/download
```

# Health
```
curl {{baseUrl}}/api/v1/health
```
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
