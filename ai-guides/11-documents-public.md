# Implementación P0 — Contrato público `/api/v1/documents`

## Objetivo

Agregar un nuevo contrato REST público y versionado para emisión de documentos tributarios:

```http
POST /api/v1/documents
GET  /api/v1/documents/{documentId}
GET  /api/v1/documents/{documentId}/status
```

La implementación debe reutilizar el flujo actual de DTE y NO debe romper, eliminar ni modificar el comportamiento existente de:

```http
/api/v1/dte
/api/v1/dte/{id}/status
/api/v1/dte/{id}/xml
/api/v1/dte/{id}/xml/regenerate
```

El nuevo endpoint será una capa pública/adaptadora sobre los servicios existentes.

---

# Principios obligatorios

1. No realizar refactorizaciones generales.
2. No renombrar clases existentes salvo que sea estrictamente necesario.
3. No eliminar endpoints existentes.
4. No modificar el pipeline actual de generación, firma, almacenamiento o envío al SII.
5. No alterar las reglas actuales de CAF, certificados, tenant o folios.
6. No introducir microservicios.
7. No crear lógica tributaria nueva dentro del controller.
8. Mantener compatibilidad hacia atrás.
9. Agregar tests antes de modificar comportamiento existente.
10. Si alguna decisión requiere modificar una clase central como `DteServiceImpl`, hacerlo sólo mediante cambios mínimos y compatibles.

---

# Arquitectura esperada

Agregar una nueva capa REST:

```text
interfaces/rest/documents
```

o equivalente coherente con la estructura actual.

La nueva API debe traducir el contrato público hacia los servicios actuales.

Flujo esperado:

```text
POST /api/v1/documents
        │
        ▼
DocumentController
        │
        ▼
PublicDocumentService
        │
        ▼
mapper / adapter
        │
        ▼
DteIngestService
        │
        ▼
pipeline existente
```

NO duplicar:

- asignación de folio;
- validación de certificados;
- persistencia de DTE;
- generación XML;
- firma;
- storage;
- enqueue;
- envío SII.

Todo eso debe seguir pasando por los servicios existentes.

---

# 1. Identificador público del documento

El identificador público será el UUID interno del DTE/documento.

Ejemplo:

```json
{
  "documentId": "20f14263-8218-47fd-87d7-24c4eaec4cdc"
}
```

Este identificador:

- NO es el folio;
- NO es el TrackID del SII;
- NO debe cambiar durante el ciclo de vida;
- debe poder utilizarse para consultar el documento y su estado.

No introducir todavía otro identificador adicional salvo que exista una razón técnica indispensable.

---

# 2. POST `/api/v1/documents`

Implementar:

```http
POST /api/v1/documents
```

Headers:

```http
Content-Type: application/json
Idempotency-Key: <string opcional inicialmente, recomendado>
```

El endpoint debe aceptar el nuevo DTO público.

Ejemplo conceptual:

```json
{
  "type": "INVOICE",
  "issuer": {
    "rutEnvia": "11111111-1"
  },
  "receiver": {
    "rut": "22222222-2",
    "businessName": "Empresa Cliente",
    "businessActivity": "Servicios",
    "address": "Dirección",
    "commune": "Viña del Mar",
    "city": "Viña del Mar",
    "email": "cliente@example.com"
  },
  "issueDate": "2026-08-11",
  "items": [
    {
      "line": 1,
      "name": "Servicio",
      "description": "Servicio mensual",
      "quantity": 1,
      "unit": "UN",
      "unitPrice": 10000,
      "amount": 10000
    }
  ],
  "totals": {
    "net": 10000,
    "vat": 1900,
    "total": 11900
  },
  "references": []
}
```

Para esta primera implementación:

```text
INVOICE -> tipoDte 33
```

No implementar todavía otros tipos documentales, pero diseñar el mapping para extender posteriormente:

```text
CREDIT_NOTE -> 61
DEBIT_NOTE  -> 56
RECEIPT     -> 39
EXEMPT_RECEIPT -> 41
```

Si se recibe un tipo aún no soportado, responder:

```http
422 Unprocessable Entity
```

con un error de dominio claro.

---

# 3. No exponer `tenantId` en el contrato público

El DTO público NO debe depender directamente de:

```json
{
  "tenantId": "...",
  "tenantCode": "..."
}
```

Sin embargo, como todavía no está implementada la autenticación/API Key pública, necesitamos mantener compatibilidad temporal.

Crear una solución transitoria claramente aislada.

Por ejemplo:

```http
X-Tenant-Id: <UUID>
```

o un resolver equivalente.

Preferir:

```text
TenantContextResolver
```

para que el controller no resuelva el tenant directamente.

Diseñar este punto para que posteriormente pueda reemplazarse por:

```text
API Key
  ↓
TenantContext
```

sin cambiar el contrato JSON de `/documents`.

NO eliminar todavía `tenantId` de los DTO internos existentes.

---

# 4. Idempotency-Key

Implementar soporte explícito para:

```http
Idempotency-Key
```

El comportamiento debe ser:

```text
tenant
+
idempotency-key
+
operation
=
resultado único
```

Para POST `/documents`, la operación puede identificarse como:

```text
CREATE_DOCUMENT
```

Crear persistencia propia para idempotencia.

Ejemplo conceptual:

```text
api_idempotency
---------------
id
tenant_id
idempotency_key
operation
document_id
created_at
```

Agregar constraint UNIQUE sobre:

```text
tenant_id
idempotency_key
operation
```

Usar Flyway para la migración.

---

# 5. Semántica de idempotencia

Primera llamada:

```http
POST /api/v1/documents
Idempotency-Key: sale-84721
```

Debe crear el documento normalmente.

Respuesta:

```http
201 Created
```

Ejemplo:

```json
{
  "documentId": "...",
  "status": "STORED",
  "folio": 145,
  "type": "INVOICE"
}
```

Si exactamente el mismo tenant vuelve a ejecutar:

```http
POST /api/v1/documents
Idempotency-Key: sale-84721
```

NO crear un segundo DTE.

Debe recuperar el documento asociado y devolver el mismo `documentId`.

La respuesta puede ser:

```http
200 OK
```

o `201` si mantener la semántica simplifica significativamente el código.

Preferir `200 OK` para replay.

---

# 6. Protección frente a requests distintos con la misma clave

No permitir silenciosamente:

```text
Idempotency-Key: abc
request A
```

y después:

```text
Idempotency-Key: abc
request B diferente
```

Agregar un hash SHA-256 del payload normalizado, por ejemplo:

```text
request_hash
```

La tabla debería evolucionar conceptualmente a:

```text
api_idempotency
---------------
id
tenant_id
idempotency_key
operation
request_hash
document_id
created_at
```

Si se reutiliza la misma clave con un payload diferente:

```http
409 Conflict
```

Error:

```json
{
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "The Idempotency-Key has already been used with a different request."
}
```

No almacenar datos sensibles completos para hacer esta comparación; almacenar solamente el hash.

---

# 7. Concurrencia

La idempotencia debe soportar llamadas concurrentes.

No implementar:

```text
SELECT
if absent
INSERT
```

sin protección de base de datos.

Utilizar el constraint UNIQUE de PostgreSQL como última barrera de concurrencia.

Dos requests simultáneos con la misma clave jamás deben emitir dos documentos.

Diseñar el servicio de forma que:

```text
request A ─┐
           ├─ misma Idempotency-Key → un único documento
request B ─┘
```

Agregar test de concurrencia si resulta razonablemente posible.

---

# 8. DTO público de respuesta

Crear un DTO específico para la API pública.

No devolver directamente entidades JPA ni `DocumentMetadata`.

Ejemplo:

```json
{
  "documentId": "20f14263-8218-47fd-87d7-24c4eaec4cdc",
  "type": "INVOICE",
  "status": "STORED",
  "folio": 145,
  "sii": {
    "trackId": null,
    "status": null
  },
  "createdAt": "2026-08-11T23:15:31Z"
}
```

Los campos que aún no existen pueden ser `null`.

No inventar información.

---

# 9. GET `/api/v1/documents/{documentId}`

Implementar consulta general del documento.

Debe devolver información pública y estable.

Ejemplo:

```json
{
  "documentId": "...",
  "type": "INVOICE",
  "status": "STORED",
  "folio": 145,
  "issueDate": "2026-08-11",
  "sii": {
    "trackId": "...",
    "status": "EPR"
  }
}
```

La consulta debe validar que el documento pertenezca al tenant resuelto.

Un tenant nunca debe poder acceder a documentos de otro tenant.

---

# 10. GET `/api/v1/documents/{documentId}/status`

Endpoint más liviano dedicado al polling.

Ejemplo:

```json
{
  "documentId": "...",
  "status": "STORED",
  "sii": {
    "trackId": null,
    "status": null,
    "message": null
  }
}
```

Debe reutilizar el estado existente.

No crear una segunda máquina de estados para la API pública.

---

# 11. Modelo de errores público

Crear un DTO consistente.

Ejemplo:

```json
{
  "timestamp": "2026-08-11T23:15:31Z",
  "status": 422,
  "code": "DOCUMENT_VALIDATION_ERROR",
  "message": "Receiver RUT is invalid",
  "path": "/api/v1/documents",
  "correlationId": "..."
}
```

Agregar códigos estables.

Inicialmente contemplar como mínimo:

```text
INVALID_REQUEST
DOCUMENT_VALIDATION_ERROR
DOCUMENT_TYPE_NOT_SUPPORTED
TENANT_NOT_FOUND
DOCUMENT_NOT_FOUND
IDEMPOTENCY_KEY_REUSED
CERTIFICATE_NOT_AVAILABLE
CAF_NOT_AVAILABLE
INTERNAL_ERROR
```

No exponer:

- stack traces;
- nombres de tablas;
- SQL;
- rutas internas;
- passwords;
- certificados;
- tokens SII;
- detalles de infraestructura.

---

# 12. HTTP status esperados

Usar:

```text
201 Created
Documento creado.

200 OK
Consulta o replay idempotente.

400 Bad Request
JSON inválido / header mal formado.

404 Not Found
Documento o recurso inexistente.

409 Conflict
Idempotency-Key reutilizada con payload diferente.

422 Unprocessable Entity
Request sintácticamente correcto pero tributariamente inválido.

500 Internal Server Error
Error inesperado.
```

No convertir todos los errores a HTTP 500.

---

# 13. Correlation ID

Aceptar opcionalmente:

```http
X-Correlation-Id
```

Si no viene, generar UUID.

Incluirlo:

- en logs;
- en respuestas de error;
- idealmente en header de respuesta.

NO agregar todavía tracing distribuido ni infraestructura adicional.

---

# 14. OpenAPI

Documentar los nuevos endpoints con OpenAPI.

Incluir:

- request;
- response;
- errores;
- `Idempotency-Key`;
- `X-Tenant-Id` temporal;
- `X-Correlation-Id`.

No eliminar ni modificar la documentación existente.

---

# 15. Tests obligatorios

Agregar tests para como mínimo:

### POST exitoso

```text
POST /documents
→ 201
→ documentId
→ DTE existente generado
```

### Replay idempotente

```text
POST key=A payload=X
→ documentId=1

POST key=A payload=X
→ mismo documentId=1
→ no segundo DTE
```

### Conflicto de idempotencia

```text
POST key=A payload=X

POST key=A payload=Y
→ 409
```

### Tenant isolation

```text
tenant A crea documento
tenant B consulta documentId
→ 404 o acceso denegado
```

Preferir 404 para no revelar existencia entre tenants.

### Tipo no soportado

```text
type=CREDIT_NOTE
→ 422 mientras todavía no esté implementado
```

### Compatibilidad existente

Los tests actuales de:

```text
/api/v1/dte
```

deben seguir pasando sin cambios.

Ejecutar:

```bash
./mvnw test
```

y no considerar terminado el requerimiento mientras exista regresión.

---

# 16. No implementar todavía

Este requerimiento NO incluye:

- API Keys;
- OAuth;
- JWT;
- usuarios;
- billing;
- rate limiting;
- webhooks;
- SDK;
- Notas de Crédito;
- Notas de Débito;
- Boletas;
- cambios al protocolo SII;
- cambios a XMLDSig;
- cambios al CAF;
- cambios a certificados;
- microservicios;
- Kafka;
- Redis.

No agregarlos aunque parezcan útiles.

---

# 17. Estrategia de implementación

Trabajar en pequeños pasos.

### Paso 1

Crear DTOs públicos y enums sin modificar flujo existente.

### Paso 2

Crear `TenantContextResolver` temporal.

### Paso 3

Crear entidad/repositorio/migración de idempotencia.

### Paso 4

Crear `PublicDocumentService`.

### Paso 5

Mapear:

```text
PublicDocumentRequest
→
DteIngestPayload
```

y delegar a:

```text
DteIngestService
```

### Paso 6

Agregar `DocumentController`.

### Paso 7

Agregar GET y status.

### Paso 8

Agregar manejo público de errores.

### Paso 9

Agregar OpenAPI.

### Paso 10

Ejecutar todos los tests y corregir exclusivamente regresiones relacionadas.

---

# 18. Restricción importante sobre código existente

Antes de modificar una clase existente:

1. explicar por qué es necesario;
2. verificar si puede resolverse mediante una clase nueva;
3. preferir composición sobre modificación;
4. mantener las firmas públicas existentes siempre que sea posible.

Especial cuidado con:

```text
DteController
DteIngestService
DteService
DteServiceImpl
CafService
SiiSubmissionProcessor
SiiSubmissionEnqueueService
```

No modificar comportamiento tributario existente dentro de estas clases para implementar la nueva API.

---

# 19. Definition of Done

El requerimiento se considera terminado solamente cuando:

```text
POST /api/v1/documents
```

pueda emitir una Factura 33 reutilizando completamente el pipeline actual;

dos requests equivalentes con la misma `Idempotency-Key` produzcan un único documento;

un tenant no pueda consultar documentos de otro;

los errores tengan contrato público estable;

exista documentación OpenAPI;

y:

./mvnw test
```

termine exitosamente incluyendo todos los tests anteriores.

Al terminar, entregar un resumen indicando:

1. archivos creados;
2. archivos modificados;
3. migraciones agregadas;
4. decisiones tomadas;
5. tests agregados;
6. cualquier deuda técnica detectada pero deliberadamente NO resuelta.