# Plantilla: Documentación de endpoint

- **Nombre:** Breve nombre del endpoint
- **Método / Ruta:** `POST /api/v1/dte` (ejemplo)
- **Descripción:** Propósito y alcance
- **Request:** esquema resumido (campos obligatorios / opcionales)
- **Response:** esquema resumido y códigos HTTP esperados
- **Errores comunes:** lista de errores y códigos
- **Idempotencia:** si aplica, cómo se logra (campo, dedup key)
- **Tests esperados:** unit + integración a incluir

Ejemplo mínimo:

- Nombre: Ingesta DTE tipo 33
- Método / Ruta: `POST /api/v1/dte`
- Request: `{ documentId, emitterRUT, receiverRUT, xmlBase64 }`
- Response: `201 Created` + `{ id, status }`
