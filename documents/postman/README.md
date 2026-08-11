# Postman — siiproxyHA

Importar los siguientes archivos en Postman:

- `documents/postman/siiproxyha-collection.json`
- `documents/postman/siiproxyha-environment.json`

## Variables del environment

- `baseUrl`: por defecto `http://localhost:8080`
- `tenantId`: UUID del tenant de prueba
- `documentId`: UUID del documento creado por la colección
- `cafId`, `cafAssignmentId`, `receptorId`, `certId`, `submissionId`: IDs auxiliares para navegación
- `rutEnvia`: RUT del usuario firmante para el flujo interno DTE
- `rutUsuario`, `nombreUsuario`, `certPassword`: datos para cargar certificados
- `idempotencyKey`: clave para probar la API pública `/api/v1/documents`

## Cobertura de la colección

La colección incluye requests para:

- health
- tenants
- receptores
- certificados en `/api/tenants/{tenantId}/certificates`
- CAF y operaciones de folios
- flujo interno DTE en `/api/v1/dte`
- API pública de documentos en `/api/v1/documents`
- consultas de `sii-submissions`

## Flujo sugerido

1. Arrancar infraestructura local con `docker compose up -d`.
2. Ejecutar la aplicación con `./mvnw spring-boot:run` o `mvn spring-boot:run`.
3. Crear un tenant con `POST Create Tenant` y guardar su `tenantId` en el environment.
4. Cargar un certificado con `POST Upload Certificate` si vas a probar el flujo interno DTE.
5. Cargar un CAF con `POST Upload CAF` y usar las requests de folios si necesitas validar asignación manual.
6. Probar el flujo interno con `POST Ingest DTE` o el flujo público con `POST Create Public Document`.
7. Usar `GET Public Document`, `GET Public Document Status`, `GET Document Status`, `GET Document XML (presigned)` o `GET SII Submissions` según el caso.

## Notas operativas

- La API pública requiere `X-Tenant-Id` y usa `Idempotency-Key` para retries seguros.
- `POST /api/v1/documents` soporta actualmente `type = INVOICE`.
- La colección guarda `documentId` automáticamente cuando una creación responde `200` o `201`.
- El flujo interno `POST /api/v1/dte` sigue vigente y utiliza `tenantId` y `rutEnvia` dentro del body.
- La ruta de certificados no lleva prefijo `/api/v1`; la forma correcta es `/api/tenants/{tenantId}/certificates`.
- Un replay de la API pública con la misma clave y el mismo payload responde `200`; la reutilización con payload distinto responde `409`.

## Curl rápidos

Crear tenant:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"acme","rutEmisor":"76184688-4","razonSocial":"ACME"}' \
  http://localhost:8080/api/v1/tenants
```

Crear documento público:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <TENANT_ID>" \
  -H "Idempotency-Key: venta-erp-000001" \
  -d '{
    "type":"INVOICE",
    "issuer":{"rutEnvia":"11111111-1"},
    "receiver":{
      "rut":"22222222-2",
      "businessName":"Cliente Uno",
      "businessActivity":"Servicios",
      "address":"Calle 123",
      "commune":"Santiago",
      "city":"Santiago",
      "email":"cliente@example.com"
    },
    "issueDate":"2026-08-11",
    "items":[{
      "line":1,
      "name":"Servicio",
      "description":"Servicio mensual",
      "quantity":1,
      "unit":"UN",
      "unitPrice":10000,
      "amount":10000
    }],
    "totals":{"net":10000,"vat":1900,"total":11900},
    "references":[]
  }' \
  http://localhost:8080/api/v1/documents
```

Consultar health:

```bash
curl http://localhost:8080/api/v1/health
```
