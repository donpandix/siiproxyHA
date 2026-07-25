# 9. Parser y material CAF

## Decisión

El archivo de autorización CAF se conserva íntegro en storage porque puede
contener la clave privada `RSASK`, pero el contrato de dominio expone solamente:

- metadatos normalizados del CAF;
- descriptor de selección;
- bloque XML público `<CAF>`.

La clave privada RSA no forma parte de DTOs ni puertos de aplicación. Se abre
únicamente dentro de un callback de infraestructura para la futura generación
de `FRMT`.

## Importación segura

`SecureCafXmlParser` acepta como raíz `<AUTORIZACION>` o un `<CAF>` público y
aplica los siguientes controles:

- límite configurable por `storage.max-caf-size`;
- procesamiento seguro JAXP;
- rechazo de `DOCTYPE`, entidades externas, DTD y esquemas externos;
- cardinalidad estricta de `CAF`, `DA`, `RNG`, `RSAPK` y `FRMA`;
- RUT emisor válido y normalizado;
- tipo DTE y rango de folios positivos;
- fecha de autorización válida;
- módulo y exponente RSA en Base64;
- `FRMA` declarado como `SHA1withRSA`;
- máximo una clave privada `RSASK`.

Al importar, `CafService` comprueba que el RUT emisor del CAF corresponda al
`rutEmisor` del tenant antes de almacenar y persistir el registro. El nombre de
archivo se reduce a un basename seguro.

## Selección del CAF

`CafMaterialAdapter` resuelve el CAF por tenant, tipo DTE, punto de venta y
folio. Si la asignación de folio aporta un `cafId`, ese identificador exacto
tiene precedencia. Sin identificador sólo se acepta un rango único; rangos
superpuestos se rechazan como ambiguos.

Antes de exponer el material público se valida:

1. estado activo y pertenencia al tenant;
2. tipo DTE, punto de venta y rango autorizado;
3. SHA-256 del objeto contra `caf_sha256`;
4. coincidencia entre XML, tenant y metadatos persistidos;
5. inclusión del folio solicitado en el rango del CAF.

La respuesta contiene una copia defensiva del bloque `<CAF>` serializado. No
incluye `RSASK` ni otros hermanos de `<CAF>`. La descarga existente usa la misma
regla y no entrega el archivo de autorización íntegro.

## Clave privada CAF

`CafPrivateKeyResolver` vuelve a cargar el CAF por su ID, revalida el descriptor
y abre `RSASK` sólo durante una operación callback. Se admiten claves RSA en
PEM PKCS#1 o PKCS#8 interpretables por Bouncy Castle.

La clave se prueba criptográficamente contra el módulo y exponente de `RSAPK`
antes de ejecutar la operación. También se compara el bloque público recién
parseado con el material seleccionado, evitando usar una autorización que haya
cambiado entre selección y firma.

Los bytes descargados se sobrescriben al finalizar. La API JCA no permite
garantizar el borrado interno del objeto `PrivateKey`, por lo que su alcance se
limita al callback.

## Fuera de este paso

Este paso no construye `DD`, no calcula `FRMT`, no firma XMLDSig y no modifica
endpoints ni esquemas de base de datos. Esas operaciones deben consumir los
contratos seguros definidos aquí en las etapas siguientes.
