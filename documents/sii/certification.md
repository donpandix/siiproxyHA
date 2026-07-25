# Plan y checklist de certificación SII

## Estado de este proyecto

**siiproxyHA no está certificado por el SII y el repositorio no contiene
evidencia de autorización.** El flujo implementado termina al almacenar
localmente un `EnvioDTE` firmado. La configuración incluye URLs de referencia,
pero no existe todavía un cliente que:

- obtenga semilla y token;
- envíe el XML al SII;
- conserve el track ID;
- consulte y concilie estados de envío o DTE;
- procese respuestas de intercambio con otros contribuyentes.

Por lo tanto, `STORED`, una validación XSD/XMLDSig correcta o una prueba de
integración verde no equivalen a `aceptado por SII`.

## Fuentes oficiales vigentes

Consulta realizada el **25 de julio de 2026**:

- [Proceso de certificación](https://www.sii.cl/factura_electronica/factura_mercado/proceso_certificacion.htm).
- [Ambiente de certificación y prueba](https://maullin.sii.cl/dte/menu.html).
- [Instructivo técnico para postulantes](https://www.sii.cl/factura_electronica/factura_mercado/instructivo.htm).
- [Documentación técnica del sistema](https://www.sii.cl/factura_electronica/tecnica.htm).
- [Formato DTE, versión 2.5 de febrero de 2026](https://www.sii.cl/factura_electronica/factura_mercado/formato_dte_202602.pdf).

Las instrucciones, esquemas y endpoints del SII pueden cambiar. Antes de cada
ciclo se debe volver a descargar la documentación y el set asignado desde el
portal oficial. No mantener copias locales como autoridad permanente.

## Proceso oficial

Para un sistema propio o de mercado, el SII certifica los DTE que la solución
emitirá, no el software en abstracto. Tras aceptar la postulación, habilita al
contribuyente y sus usuarios autorizados en el ambiente de certificación.

El proceso publicado por SII contempla:

1. set de pruebas asignado por SII;
2. set de simulación;
3. set de intercambio de información;
4. envío de muestras de impresión;
5. declaración de cumplimiento de requisitos;
6. registro como emisor electrónico.

El avance se declara en el portal. La autorización o registro final del SII es
la única evidencia válida de término; no debe inferirse desde resultados
locales.

## Prerrequisitos de entrada

- [ ] Postulación aceptada para el RUT contribuyente.
- [ ] Usuario administrador y usuarios autorizados registrados.
- [ ] Certificado digital vigente y disponible bajo custodia controlada.
- [ ] Acceso confirmado al ambiente de certificación.
- [ ] Set de pruebas vigente descargado desde el portal.
- [ ] Datos para construcción de DTE obtenidos desde el ambiente oficial.
- [ ] CAF y rangos de prueba obtenidos para el RUT y tipos a certificar.
- [ ] Tipos de DTE que se presentarán definidos. El alcance actual del proyecto
      solo implementa tipo 33.
- [ ] Responsable tributario, responsable técnico y custodio de credenciales
      designados.
- [ ] Política de evidencia, respaldo, retención y sanitización aprobada.

## Matriz de preparación del proyecto

| Capacidad exigida o necesaria | Estado | Evidencia local / brecha |
| --- | --- | --- |
| Construcción de Factura Electrónica tipo 33 | Implementada localmente | Constructor DOM y XSD locales. Debe contrastarse nuevamente con el formato y schemas vigentes del SII. |
| TED y firma `FRMT` con CAF | Implementada localmente | Validación criptográfica sobre los bytes de `DD`. |
| Firma XMLDSig de `Documento` y `SetDTE` | Implementada localmente | RSA-SHA1 y canonicalización exigidos por los schemas incluidos. |
| Consistencia `RutEnvia` con certificado | Implementada localmente | Se valida certificado incorporado, vigencia y RUT del sujeto. |
| Cadena de confianza y revocación X.509 | Pendiente | El validador local no consulta una autoridad de confianza ni revocación. |
| Verificación oficial de `CAF/FRMA` | Pendiente | No se configura la clave/cadena oficial necesaria. |
| Almacenamiento, checksum e idempotencia | Implementada localmente | PostgreSQL + MinIO; replay estable por `id`. |
| Autenticación automática SII | No implementada | Faltan semilla, firma, token, expiración y manejo seguro de sesión. |
| Upload de `EnvioDTE` | No implementado | No hay cliente HTTP ni persistencia de track ID. |
| Consulta y conciliación de estado | No implementada | `SENT` es futuro; no hay estados oficiales persistidos. |
| Set de pruebas asignado | Pendiente de ejecución externa | Requiere postulación, datos y CAF del contribuyente en SII. |
| Set de simulación | Pendiente | Requiere envío y consulta real en certificación. |
| Intercambio entre contribuyentes | No implementado | Faltan recepción/envío de mensajes y acuses exigidos. |
| Representación impresa y muestras | No implementada | El proyecto es backend XML y no genera PDF/impresión. |
| Declaración de cumplimiento | Bloqueada | Solo procede después de completar y documentar las pruebas. |
| Registro como emisor electrónico | Bloqueado | Depende de aprobación/autorización del SII. |

## Gate técnico previo al set de pruebas

No iniciar un ciclo formal mientras exista un punto bloqueante:

- [ ] Actualizar los XSD locales desde la publicación oficial, revisar su
      integridad y ejecutar regresión completa.
- [ ] Comparar todos los campos tipo 33 implementados con la versión vigente
      del formato DTE y documentar campos no soportados.
- [ ] Implementar autenticación SII con certificado, sin exponer claves ni
      tokens.
- [ ] Implementar upload al ambiente de certificación con límites, timeouts,
      idempotencia y captura íntegra de respuesta.
- [ ] Persistir track ID separado del estado local y modelar los estados
      oficiales sin confundir `recibido` con `aceptado`.
- [ ] Implementar consulta con backoff, plazo máximo y conciliación auditable.
- [ ] Incorporar pruebas de contrato contra fixtures oficiales y pruebas de
      integración opt-in al ambiente de certificación.
- [ ] Resolver la validación de confianza/revocación X.509 y `CAF/FRMA` o dejar
      aprobado por seguridad el control compensatorio.
- [ ] Implementar el intercambio entre contribuyentes requerido para el set.
- [ ] Implementar y validar la representación impresa contra el manual vigente.
- [ ] Separar completamente configuración y credenciales de local,
      certificación y producción.

Los endpoints configurados en `application.yaml` son placeholders, no prueba de
que el protocolo esté implementado ni de que una URL sea la vigente para una
operación determinada.

## Ejecución por etapa

### 1. Set asignado

- Crear una rama/release inmutable para el ciclo.
- Cargar exclusivamente el certificado, CAF, contribuyente y folios del
  ambiente de certificación.
- Traducir cada caso del set a un request reproducible.
- Emitir sin editar manualmente el XML.
- Validar localmente y enviar mediante el adaptador SII.
- Conservar XML, SHA-256, track ID, respuesta y estado final.
- Corregir la causa y regenerar con un identificador controlado cuando SII
  rechace un caso; no alterar artefactos ya firmados.

Criterio de salida: todos los casos solicitados aparecen exitosos en el portal y
la evidencia local permite relacionar cada caso con su envío.

### 2. Simulación

- Ejecutar los casos operativos indicados por SII con fechas, montos y
  referencias del set.
- Probar reintentos ante timeout sin duplicar el negocio.
- Conciliar cada estado local con su track ID y estado oficial.

Criterio de salida: simulación completada y declarada según el portal, sin
folios huérfanos ni estados ambiguos.

### 3. Intercambio

- Usar únicamente contrapartes y mensajes definidos para certificación.
- Registrar entrega, recepción, acuses y respuestas.
- Verificar autenticidad, correlación y plazos de cada mensaje.

Criterio de salida: intercambio completo aceptado por SII. Este punto exige
desarrollo adicional en el proyecto actual.

### 4. Muestras impresas

- Generar la representación desde el mismo DTE aceptado, no desde datos
  reingresados.
- Validar timbre, datos obligatorios, paginación y legibilidad contra el Manual
  de Muestras Impresas vigente.
- Conservar el archivo presentado y su relación con XML/track ID.

Criterio de salida: muestras presentadas y aceptadas. El proyecto actual no
genera esta representación.

### 5. Declaración y registro

- Revisar que emisión, recepción, administración, contingencia, seguridad,
  respaldo y soporte estén operativos.
- Obtener aprobación formal de responsables técnico y tributario.
- Declarar cumplimiento y avance solo con evidencia completa.
- Archivar la resolución o confirmación de registro emitida por SII.

Criterio de salida: contribuyente registrado/autorizado como emisor electrónico
para los tipos de DTE aprobados.

## Registro mínimo de evidencias

| Campo | Ejemplo o regla |
| --- | --- |
| `cycleId` | Identificador interno inmutable del ciclo. |
| Versión | Commit, imagen y configuración no secreta. |
| Ambiente | `SII_CERTIFICATION`; nunca inferido desde una URL. |
| Caso SII | Identificador exacto del set. |
| Documento local | `documentId`, folio y `objectKey`. |
| Integridad | SHA-256 del XML exacto enviado. |
| Envío | Fecha/hora, track ID y respuesta original sanitizada. |
| Resultado | Estado oficial, glosas/códigos y fecha de consulta. |
| Operador | Identidad auditable, no una cuenta compartida. |
| Corrección | Incidente, causa, cambio y nuevo envío relacionado. |

Nunca almacenar en esta evidencia contraseñas, claves privadas, `RSASK`, tokens
de sesión o CAF completos. Los XML pueden contener datos personales y
tributarios; su acceso y retención deben estar controlados.

## Criterio para afirmar “certificado”

Solo se puede declarar que el contribuyente está certificado/autorizado cuando:

- el portal SII muestra todas las etapas requeridas completadas;
- la declaración de cumplimiento fue presentada;
- SII registró o autorizó al contribuyente como emisor para los tipos
  correspondientes;
- la resolución o confirmación oficial está archivada y vinculada a la versión
  liberada.

Hasta entonces, la denominación correcta es: **“flujo local compatible en
evaluación, pendiente de certificación SII”**.

Para operar la parte implementada, consultar
[`documents/operations/dte-signing-runbook.md`](../operations/dte-signing-runbook.md).
