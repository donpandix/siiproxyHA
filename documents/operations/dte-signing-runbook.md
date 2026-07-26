# Runbook operativo de emisión y firma DTE

## Propósito y alcance

Este runbook cubre la operación local del flujo implementado para Factura
Electrónica tipo 33:

1. registro del tenant;
2. carga de un certificado PKCS#12 autorizado para `RutEnvia`;
3. carga de un CAF con su clave privada `RSASK`;
4. asignación de folio;
5. construcción de TED y `FRMT`;
6. firma XMLDSig de `Documento` y `SetDTE`;
7. validación integral local;
8. almacenamiento del `EnvioDTE` en MinIO;
9. consulta de estado y recuperación del XML;
10. replay idempotente por `id`.

No cubre autenticación, envío, consulta de track ID ni aceptación/reparo/rechazo
en el SII. El estado operativo terminal del flujo actual es `STORED`; `SENT`
está reservado para una integración futura.

## Responsables

| Rol | Responsabilidad |
| --- | --- |
| Operador de plataforma | PostgreSQL, MinIO, variables de entorno, backups y observabilidad. |
| Operador tributario | Datos del emisor, resolución, CAF, folios y coordinación de certificación. |
| Custodio de credenciales | PKCS#12, contraseña, rotación, revocación y control de acceso. |
| Desarrollo | Diagnóstico de validación, firma, idempotencia y errores no operables. |

Una misma persona puede cumplir varios roles en desarrollo, pero no se
recomienda hacerlo en ambientes compartidos o productivos.

## Preparación

Requisitos:

- Java 21;
- Docker;
- `.env` creado desde `.env.example`;
- PostgreSQL y MinIO disponibles;
- un PKCS#12 RSA vigente cuyo RUT de sujeto corresponda a `RutEnvia`;
- un CAF tipo 33 válido para el RUT emisor y con `RSASK`.

Levantar y verificar:

```bash
cp .env.example .env
docker compose up -d
docker compose ps
./mvnw spring-boot:run
```

En otra terminal:

```bash
curl --fail http://localhost:8080/api/v1/health
curl --fail http://localhost:8080/actuator/health
```

Una respuesta `UP` prueba disponibilidad de la aplicación, no la validez de
credenciales ni la interoperabilidad con SII.

## Custodia de secretos

- Guardar CAF y PKCS#12 reales solo en un gestor de secretos o, para desarrollo
  local, bajo `local-secrets/`, que está excluido de Git.
- No usar las claves de ejemplo de `.env.example` fuera del entorno local.
- Generar `ENCRYPTION_MASTER_KEY` con al menos 32 bytes aleatorios y administrarla
  separada de los backups de base de datos.
- No registrar contraseñas, contenido PKCS#12, `RSASK`, XML de CAF ni variables
  completas en tickets o logs.
- Antes de eliminar o reemplazar un certificado, comprobar qué emisor y
  `RutEnvia` dependen de él. La eliminación mediante API es material y no debe
  usarse como mecanismo de rotación sin respaldo.
- Si se sospecha exposición, revocar el certificado con su proveedor, bloquear
  la credencial en la aplicación y rotar las claves de cifrado según el
  procedimiento de seguridad de la organización.

El fixture `documents/samples/FoliosSII_33.xml` está sanitizado: permite probar
el registro y la asignación, pero no generar `FRMT`.

## Alta mínima del emisor

Crear el tenant con los datos tributarios efectivos del ambiente. No copiar los
valores de este ejemplo a certificación o producción:

```bash
curl --fail-with-body \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantCode": "emisor-cert",
    "rutEmisor": "76184688-4",
    "razonSocial": "EMISOR DE PRUEBA",
    "giro": "SERVICIOS",
    "acteco": "620200",
    "direccion": "DIRECCION DE PRUEBA 123",
    "comuna": "SANTIAGO",
    "ciudad": "SANTIAGO",
    "email": "operaciones@example.invalid",
    "fchResol": "2014-08-22",
    "nroResol": 80,
    "active": true
  }' \
  http://localhost:8080/api/v1/tenants
```

Conservar el `id` retornado como `TENANT_ID`. `fchResol` y `nroResol` deben
provenir de los antecedentes aplicables al contribuyente y al ambiente; no
deben inferirse.

## Carga y control del PKCS#12

Evitar escribir la contraseña en el historial:

```bash
read -s CERT_PASSWORD
curl --fail-with-body \
  -F "file=@local-secrets/emisor.p12" \
  -F "rutUsuario=76184688-4" \
  -F "nombreUsuario=Firmante autorizado" \
  -F "password=${CERT_PASSWORD}" \
  -F "isDefault=true" \
  -F "createdBy=operacion" \
  "http://localhost:8080/api/tenants/${TENANT_ID}/certificates"
unset CERT_PASSWORD
```

Verificar metadatos, vigencia y condición activa:

```bash
curl --fail \
  "http://localhost:8080/api/tenants/${TENANT_ID}/certificates"
```

La carga correcta no sustituye la comprobación posterior de autorización:
durante la firma se exige que `rutUsuario`, el RUT del certificado y
`RutEnvia` sean consistentes.

## Carga y control del CAF

```bash
curl --fail-with-body \
  -F "tenantId=${TENANT_ID}" \
  -F "puntoVenta=1" \
  -F "file=@local-secrets/FoliosSII_33.xml" \
  http://localhost:8080/api/v1/caf
```

Conservar el `id` retornado como `CAF_ID` y revisar el rango disponible:

```bash
curl --fail \
  "http://localhost:8080/api/v1/caf/folios/status?tenantId=${TENANT_ID}&tipoDte=33&puntoVenta=1"
```

No editar manualmente un CAF, su rango o sus claves. El checksum almacenado se
comprueba antes de usar el material criptográfico.

## Emisión controlada

La colección `documents/postman/siiproxyha-collection.json` contiene el request
`POST Ingest DTE` y captura el identificador retornado. Para automatización se
puede usar:

```bash
DOCUMENT_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"

curl --fail-with-body \
  -H 'Content-Type: application/json' \
  -d "{
    \"id\": \"${DOCUMENT_ID}\",
    \"tenantId\": \"${TENANT_ID}\",
    \"tenantCode\": \"emisor-cert\",
    \"rutEnvia\": \"76184688-4\",
    \"tipoDte\": 33,
    \"fchEmis\": \"2026-07-25\",
    \"receptor\": {
      \"rutReceptor\": \"60803000-K\",
      \"razonSocial\": \"RECEPTOR DE PRUEBA\",
      \"giro\": \"SERVICIOS PUBLICOS\",
      \"email\": \"receptor@example.invalid\",
      \"telefono\": \"223951000\",
      \"direccion\": \"DIRECCION DE PRUEBA 456\",
      \"comuna\": \"SANTIAGO\",
      \"ciudad\": \"SANTIAGO\"
    },
    \"items\": [{
      \"nroLinDet\": 1,
      \"nmbItem\": \"Servicio de prueba\",
      \"qtyItem\": 1,
      \"unmdItem\": \"UN\",
      \"prcItem\": 7000,
      \"montoItem\": 7000
    }],
    \"mntNeto\": 7000,
    \"iva\": 1330,
    \"mntTotal\": 8330
  }" \
  http://localhost:8080/api/v1/dte
```

Para certificación, reemplazar todos los datos por los entregados o autorizados
por SII en el set vigente. No reutilizar folios ni identidades de otro ambiente.

Resultado esperado:

- HTTP `201`;
- `status` igual a `STORED`;
- `objectKey`, `sha256` y `sizeBytes` informados;
- `attemptCount` igual a `1` en el primer intento.

Consultar y descargar:

```bash
curl --fail \
  "http://localhost:8080/api/v1/dte/${DOCUMENT_ID}/status"

curl --fail \
  -H 'Accept: application/xml' \
  "http://localhost:8080/api/v1/dte/${DOCUMENT_ID}/xml" \
  -o "local-secrets/${DOCUMENT_ID}.xml"
```

## Regenerar un XML firmado existente

Cuando cambie el formato de construcción, se corrija una incompatibilidad o se
rote la credencial firmante, regenerar el artefacto sin crear otro DTE:

```bash
curl --fail-with-body \
  -X POST \
  "http://localhost:8080/api/v1/dte/${DOCUMENT_ID}/xml/regenerate"
```

La operación:

- exige que el DTE conserve su asignación de folio, pool y CAF;
- no asigna otro folio ni modifica el snapshot tributario;
- vuelve a generar TED/FRMT y las firmas de `Documento` y `SetDTE`;
- usa la credencial activa vigente para el `RutEnvia` persistido;
- valida integralmente el nuevo `EnvioDTE`;
- reemplaza el objeto bajo el mismo `objectKey`;
- incrementa `attemptCount` y registra las transiciones en
  `processing_history`.

Un `409` indica que hay otra regeneración activa o que el estado actual no
admite la operación. Un claim `PENDING_STORE` puede recuperarse después de
cinco minutos. Después de una respuesta `200`, descargar nuevamente el XML y
usar el nuevo `sha256`; una copia obtenida antes de la regeneración queda
obsoleta.

## Idempotencia y reintentos

- El cliente debe generar un `id` estable antes del primer envío.
- Ante timeout o respuesta ambigua, repetir exactamente el mismo request y el
  mismo `id`.
- Si el documento ya está `STORED`, el replay retorna sus metadatos sin
  consumir otro folio ni reescribir el artefacto.
- Un estado `FAILED_RECOVERABLE` permite reintento con el mismo `id`.
- Un claim `PENDING_STORE` se puede recuperar cuando queda obsoleto; el lease
  actual es de cinco minutos.
- No cambiar datos tributarios conservando el mismo `id`: debe tratarse como
  conflicto, no como corrección.
- No liberar manualmente un folio `USED`. Los folios ya incorporados a un DTE
  no se reciclan.

## Diagnóstico

| Síntoma | Comprobación | Acción |
| --- | --- | --- |
| `SIGNER_AUTHORIZATION` o no hay certificado elegible | Listar certificados del tenant; comparar `RutEnvia`, `rutUsuario`, RUT del sujeto y vigencia. | Corregir el dato o cargar una credencial autorizada. No omitir la validación. |
| PKCS#12 o contraseña inválidos | Revisar formato, contraseña y presencia de clave privada RSA fuera de la aplicación. | Obtener una copia válida del custodio; no convertir ni extraer la clave en un host no controlado. |
| CAF ausente o folio fuera de rango | Consultar `/api/v1/caf/folios/status`. | Cargar el CAF correcto para RUT, tipo 33 y rango; no modificar el XML. |
| `TED_FRMT_INVALID` | Revisar que el CAF tenga `RSASK` y que no haya sido alterado. | Reponer el CAF original autorizado. |
| `XSD_VALIDATION` | Descargar el XML y contrastar el código de validación con el payload. | Corregir datos o constructor; no editar el XML ya firmado. |
| `FAILED_RECOVERABLE` por storage | Verificar MinIO, bucket, permisos y red. | Restaurar el servicio y reenviar el mismo `id`. |
| `PENDING_STORE` reciente | Revisar `updated_at` y salud de MinIO. | Esperar el intento en curso; no lanzar replays concurrentes. |
| `PENDING_STORE` por más de cinco minutos | Confirmar que no existe otro proceso activo. | Reenviar el mismo request e `id`; el claim obsoleto puede ser recuperado. |
| `FAILED_FATAL` por checksum | Comparar metadata con el objeto y revisar integridad de MinIO. | Preservar evidencias, bloquear el artefacto y escalar a desarrollo/seguridad. |
| Estado local `STORED`, sin track ID SII | Comportamiento esperado del alcance actual. | No marcar como enviado o aceptado; falta implementar el adaptador SII. |

Consultas de solo lectura para diagnóstico:

```sql
select document_id, folio, status, object_key, sha256, size_bytes,
       attempt_count, last_error, created_at, updated_at
  from document_metadata
 where document_id = '<DOCUMENT_ID>';

select from_state, to_state, actor, notes, created_at
  from processing_history
 where document_id = '<DOCUMENT_ID>'
 order by created_at, id;
```

No corregir estados, checksums ni folios directamente en base de datos.

## Respaldo, recuperación y evidencia

Respaldar como una unidad consistente:

- PostgreSQL, incluidos `document_metadata`, `processing_history`, CAF,
  asignaciones y credenciales;
- los buckets `dte-bucket` y `certificates-bucket`;
- la configuración no secreta y la versión desplegada;
- la clave maestra mediante el mecanismo seguro de la organización.

Sin la clave maestra no se puede resolver la contraseña cifrada del PKCS#12; sin
los objetos MinIO, la metadata no permite reconstruir los artefactos.

Para cada ejecución de certificación conservar:

- versión o commit desplegado;
- set y folios entregados por SII;
- request sanitizado;
- XML exacto y su SHA-256;
- historial local;
- respuesta, track ID y estados SII, cuando exista el adaptador;
- fecha, operador, ambiente y resultado.

## Verificación antes de promover

```bash
./mvnw -q verify
./mvnw -q \
  -Dtest=cl.cesarg.siiproxyHA.application.service.DteEmissionIntegrationTest \
  test
```

La prueba de integración requiere Docker y puede omitirse si no está
disponible. Un resultado local exitoso demuestra el flujo de generación,
firma, validación, persistencia y replay probado por el repositorio; no
demuestra certificación ni aceptación por SII.

El checklist de brechas y evidencias externas está en
[`documents/sii/certification.md`](../sii/certification.md).
