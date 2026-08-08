# Envío automático de `EnvioDTE` al SII

## Alcance de esta entrega

Después de generar, firmar, validar y almacenar un `EnvioDTE`, la aplicación
crea automáticamente un trabajo persistente para:

1. obtener semilla y token con el mismo certificado que firmó el XML;
2. subir al SII los bytes exactos almacenados en MinIO;
3. conservar el `TRACKID` de recepción;
4. consultar `QueryEstUp` hasta obtener un estado terminal.
5. reconciliar por `QueryEstDteAv` cualquier upload cuyo resultado sea ambiguo,
   antes de decidir si corresponde reenviar.

La configuración predeterminada apunta exclusivamente a certificación. El
acceso a producción requiere simultáneamente:

```text
SII_ENVIRONMENT=PRODUCTION
SII_PRODUCTION_ENABLED=true
```

No se debe habilitar producción hasta completar la certificación operacional.

## Arquitectura asíncrona

La petición que crea el DTE termina cuando el XML queda almacenado y el trabajo
SII queda en PostgreSQL. Un worker programado reclama trabajos mediante bloqueo
de filas con `FOR UPDATE SKIP LOCKED`.

Esto permite ejecutar una sola instancia durante el MVP y agregar instancias
posteriormente sin que dos workers reclamen el mismo envío. El caché del token
es local a cada proceso y es solamente una optimización: PostgreSQL sigue
siendo la fuente de verdad del trabajo pendiente.

## Variables de ambiente

| Variable | Predeterminado | Uso |
|---|---|---|
| `SII_ENVIRONMENT` | `CERTIFICATION` | Ambiente cerrado: `CERTIFICATION` o `PRODUCTION`. |
| `SII_PRODUCTION_ENABLED` | `false` | Interruptor adicional para bloquear producción. |
| `SII_WORKER_ENABLED` | `true` | Activa el procesamiento de la cola. |
| `SII_CONNECT_TIMEOUT` | `10s` | Timeout al establecer conexión. |
| `SII_REQUEST_TIMEOUT` | `30s` | Timeout de cada llamada. |
| `SII_TOKEN_TTL` | `55m` | Vigencia conservadora del caché local. |
| `SII_WORKER_DELAY` | `2s` | Espera entre búsquedas de trabajo. |
| `SII_CLAIM_LEASE` | `5m` | Umbral para detectar un worker abandonado. |
| `SII_MAX_UPLOAD_ATTEMPTS` | `5` | Máximo total de intentos de upload, incluido el inicial. |
| `SII_UPLOAD_RETRY_INITIAL_DELAY` | `30s` | Espera inicial para un reintento seguro. |
| `SII_UPLOAD_RETRY_MAX_DELAY` | `5m` | Tope del backoff exponencial de upload. |
| `SII_MAX_RECONCILIATION_ATTEMPTS` | `5` | Máximo de consultas para resolver un resultado ambiguo. |
| `SII_RECONCILIATION_INITIAL_DELAY` | `2m` | Espera inicial antes de consultar por el DTE. |
| `SII_RECONCILIATION_MAX_DELAY` | `30m` | Tope del backoff de reconciliación. |
| `SII_MAX_STATUS_QUERIES` | `30` | Máximo de consultas por `TRACKID`. |
| `SII_CERT_SEED_URL` | Maullín `CrSeed.jws` | Semilla de certificación. |
| `SII_CERT_TOKEN_URL` | Maullín `GetTokenFromSeed.jws` | Token de certificación. |
| `SII_CERT_UPLOAD_URL` | Maullín `DTEUpload` | Upload de certificación. |
| `SII_CERT_STATUS_URL` | Maullín `QueryEstUp.jws` | Consulta de certificación. |
| `SII_CERT_DTE_STATUS_URL` | Maullín `QueryEstDteAv` | Reconciliación por datos y firma del Documento. |

Los endpoints se validan como HTTPS bajo `sii.cl` durante el inicio. No pueden
ser enviados como parámetros de la API.

## Estados persistidos

| Estado | Significado |
|---|---|
| `PENDING_UPLOAD` | XML almacenado y pendiente de autenticación/upload. |
| `UPLOADING` | Un worker reclamó el upload. |
| `RECEIVED` | El SII respondió `STATUS=0` y entregó `TRACKID`; espera consulta. |
| `STATUS_QUERYING` | Un worker está ejecutando `QueryEstUp`. |
| `PROCESSED` | `QueryEstUp` devolvió `EPR` sin documentos rechazados. |
| `REJECTED` | El upload fue rechazado o se recibió `RSC`, `RFR` o `RCT`. |
| `OUTCOME_UNKNOWN` | El upload pudo llegar al SII, pero no existe confirmación segura; espera reconciliación. |
| `RECONCILING` | Un worker consulta `QueryEstDteAv` sin reenviar el XML. |
| `MANUAL_REVIEW_REQUIRED` | Cinco consultas no resolvieron el resultado o el SII informó datos incompatibles. |
| `FAILED_RECOVERABLE` | Se agotaron intentos seguros de preparación/autenticación/consulta. |
| `FAILED_FATAL` | Falla no recuperable del trabajo. |

Un `EPR` con `RECHAZADOS > 0` termina como `REJECTED`. Los contadores
`INFORMADOS`, `ACEPTADOS`, `RECHAZADOS` y `REPAROS` quedan disponibles en la
API. Los estados `PDR`, `SOK`, `CRT` y `FOK` mantienen el envío en seguimiento.
La primera consulta se programa después de dos minutos para archivos menores a
30 KiB y después de seis minutos para archivos mayores o iguales.

Los fallos seguros se reintentan con backoff de 30, 60, 120 y 240 segundos. Un
timeout o corte posterior al inicio del upload no se reenvía directamente: se
consulta al SII con emisor, receptor, tipo, folio, fecha, monto y la firma del
`Documento`. Solo dos respuestas explícitas consecutivas de “no recibido”
permiten volver a encolar los mismos bytes, siempre respetando el máximo total
de cinco uploads. El tamaño y SHA-256 del artefacto se verifican antes de cada
operación.

## Observación por API

```http
GET /api/v1/dte/{dteId}/sii-submissions
GET /api/v1/dte/{dteId}/sii-submissions/{submissionId}
```

Las respuestas muestran estados, contadores, `TRACKID`, código/glosa del SII,
hash de la respuesta y errores redactados. No exponen token, contraseña,
material privado ni el XML completo.

## Logs

Los logs se escriben a stdout para que Docker/AWS los capture. Las líneas
principales incluyen:

```text
SII certification upload received submission=... dte=... trackId=... status=0
SII certification submission processed submission=... dte=... trackId=...
SII certification submission rejected submission=... dte=... trackId=... status=...
SII certification upload transport failure submission=... dte=... outcomeUnknown=true
```

No se registra la semilla, el token, la contraseña, la clave privada ni el XML.
Las respuestas remotas completas se almacenan en MinIO bajo
`sii-responses/{dteId}/{submissionId}/` y PostgreSQL conserva su SHA-256.

## Diagnóstico PostgreSQL

Trabajos recientes:

```sql
select id,
       dte_id,
       environment,
       status,
       attempt_count,
       status_query_count,
       reconciliation_count,
       track_id,
       sii_status,
       sii_glosa,
       last_error,
       next_attempt_at,
       updated_at
from sii_submission
order by created_at desc
limit 50;
```

Trabajos que requieren intervención:

```sql
select *
from sii_submission
where status in ('OUTCOME_UNKNOWN', 'MANUAL_REVIEW_REQUIRED', 'FAILED_RECOVERABLE', 'FAILED_FATAL')
order by updated_at desc;
```

Un `OUTCOME_UNKNOWN` se reconcilia automáticamente. No debe cambiarse
manualmente a `PENDING_UPLOAD`; si las consultas se agotan, el trabajo pasa a
`MANUAL_REVIEW_REQUIRED` y requiere comprobación operacional.

## Verificación de certificación pendiente

Las pruebas automatizadas usan servidores HTTP simulados. Antes de considerar
completa la certificación operacional se debe ejecutar con credenciales reales
de certificación y comprobar:

1. respuesta `STATUS=0`;
2. persistencia del `TRACKID`;
3. transición posterior de `QueryEstUp`;
4. ausencia de token/XML en CloudWatch;
5. respuesta almacenada en MinIO y hash coincidente.
