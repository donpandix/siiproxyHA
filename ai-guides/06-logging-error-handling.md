# 6. Reglas de logging y manejo de errores

Logging:

- Formato estructurado (JSON) para producción; texto legible en `local`.
- Niveles: ERROR (fallos), WARN (eventos importantes), INFO (flujos usuales), DEBUG (detallado para dev).
- Mensajes deben incluir: `traceId`/`requestId`, `userId` cuando aplique, y contexto mínimo necesario.

Errores y excepciones:

- Normalizar respuestas de error con un esquema: `{ code, message, details?, timestamp }`.
- Mapear excepciones de negocio a códigos HTTP adecuados (400 para validación, 404 para no encontrado, 409 conflicto, 500 error interno).
- No exponer stack traces ni datos sensibles en respuestas públicas.

Observabilidad:

- Incluir correlation IDs en cabeceras (ej: `X-Request-Id`).
- Exponer `/actuator/health` y métricas básicas.
