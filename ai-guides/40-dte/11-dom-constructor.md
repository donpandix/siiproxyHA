# 11. Constructor DOM de EnvioDTE

## Decisión

La construcción XML deja de realizarse mediante concatenación de cadenas en
`DteServiceImpl`. El flujo queda separado en:

- `DteXmlAssemblyService`: mapea el snapshot persistido y coordina
  `TedGeneratorPort` con `DteXmlBuilderPort`;
- `DomDteXmlBuilderAdapter`: construye y serializa el árbol XML;
- `DteServiceImpl`: almacena o entrega los bytes resultantes.

Los contratos de dominio exponen bytes, datos inmutables e identificadores,
pero nunca `Document`, `Element`, factories JAXP ni otros tipos DOM.

## Estructura construida

El adaptador genera, en orden:

1. `EnvioDTE`;
2. `SetDTE` y `Caratula`;
3. `DTE`;
4. `Documento`;
5. `Encabezado`, detalles y referencias;
6. `TED` ya firmado;
7. `TmstFirma`.

Todos los elementos pertenecen explícitamente al namespace
`http://www.sii.cl/SiiDte`. Los atributos `SetDTE/@ID` y `Documento/@ID` se
marcan como tipo ID en el DOM y se retornan junto al XML para que la etapa
XMLDSig use referencias internas inequívocas.

La marca DOM de tipo ID no sobrevive a la serialización; el futuro adaptador
XMLDSig deberá volver a registrar el atributo después de parsear los bytes,
usando los identificadores retornados por este contrato.

## Importación del TED

El TED se procesa con JAXP seguro:

- namespace awareness habilitado;
- `DOCTYPE`, entidades externas, DTD y esquemas externos deshabilitados;
- codificación de entrada fijada en `ISO-8859-1`;
- raíz `TED`, versión `1.0`, un único `DD` y un único `FRMT`;
- perfil `FRMT` limitado a `SHA1withRSA`;
- rechazo de namespaces, atributos o nodos inesperados en el fragmento.

Los elementos se copian al namespace SII antes de incorporarlos al documento.
No se realiza interpolación textual del fragmento.

## Invariante criptográfica

Antes de importar, el `DD` contenido en `tedXml` debe coincidir byte a byte con
el `ddXml` firmado. Después de serializar el DOM, el constructor vuelve a
extraer `DD` y exige la misma igualdad.

Si el serializador compacta, reescapa o modifica el contenido firmado, la
construcción falla con `DD_CHANGED`; nunca se entrega un documento cuyo `FRMT`
haya quedado inválido silenciosamente.

## Serialización

- codificación: `ISO-8859-1`;
- declaración XML incluida;
- `standalone="no"`;
- sin indentación automática;
- acceso externo de Transformer deshabilitado.

## Límites

El resultado aún no contiene firmas XMLDSig de `Documento` ni `SetDTE`.
Tampoco se ejecuta todavía validación XSD del sobre completo. No se modifican
endpoints, DTOs HTTP ni el esquema de base de datos.
