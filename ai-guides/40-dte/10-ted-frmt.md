# 10. Generación de TED y FRMT

## Decisión

La aplicación entrega al puerto `TedGeneratorPort` únicamente los datos
tributarios del timbre y el identificador exacto del CAF asociado a la
asignación de folio. El adaptador de infraestructura:

1. resuelve y revalida el CAF;
2. construye el bloque `DD`;
3. abre la clave privada CAF dentro de un callback;
4. firma los bytes exactos de `DD`;
5. retorna el `TED` completo sin exponer la clave.

La firma `FRMT` es independiente de XMLDSig y de la credencial PKCS#12 usada
para firmar `Documento` o `SetDTE`.

## Estructura de DD

`DD` se construye en el orden exigido por `DTE_v10.xsd`:

1. `RE`: RUT emisor normalizado.
2. `TD`: tipo DTE.
3. `F`: folio asignado.
4. `FE`: fecha de emisión.
5. `RR`: RUT receptor normalizado.
6. `RSR`: razón social del receptor.
7. `MNT`: monto total.
8. `IT1`: nombre del primer ítem.
9. `CAF`: bloque público seleccionado.
10. `TSTED`: timestamp local de generación.

`RSR` e `IT1` se recortan a 40 caracteres, que es el máximo declarado por el
XSD. Todos los valores de texto se escapan como contenido XML.

## Codificación y firma

Los bytes firmados y almacenados usan `ISO-8859-1`. El adaptador rechaza
caracteres que no puedan representarse en esa codificación para impedir
reemplazos silenciosos que cambien el contenido tributario.

`DD` y `TED` se renderizan con saltos de línea LF (`\n`) deterministas. El
bloque público `CAF` normaliza CRLF y CR a LF antes de incorporarse. Esos
saltos son parte de los bytes de `DD`: `FRMT` se calcula después de construir
el bloque definitivo y ninguna etapa posterior puede reindentarlo.

El perfil requerido por el SII para CAF RSA es:

- algoritmo JCA: `SHA1withRSA`;
- entrada: bytes completos desde `<DD>` hasta `</DD>`;
- salida: firma RSA codificada en Base64;
- atributo XML: `algoritmo="SHA1withRSA"`.

SHA-1 queda encapsulado exclusivamente en este perfil legado y no se usa como
algoritmo general de la aplicación.

## Integración con el DTE

`DteServiceImpl` exige que el DTE tenga una asignación de folio vinculada a un
pool y a un CAF. El `cafId` de esa relación se entrega al selector para evitar
resolver rangos superpuestos de forma implícita.

El timestamp retornado por el generador se reutiliza en:

- `DD/TSTED`;
- `Documento/TmstFirma`;
- `Caratula/TmstFirmaEnv`.

La zona horaria se configura mediante `dte.signing-zone` y por defecto es
`America/Santiago`.

## Límites

Este paso no agrega todavía XMLDSig sobre `Documento` ni `SetDTE`, tampoco
valida el XML completo contra XSD ni lo envía al SII. No modifica endpoints,
DTOs HTTP ni el esquema de base de datos.
