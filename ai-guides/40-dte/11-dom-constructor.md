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

Los identificadores siguen una convención estable y compatible con los
ejemplos aceptados por el SII:

- `SetDTE/@ID`: `SIIPROXY_SetDoc`;
- `Documento/@ID`: `DTE_T{TipoDTE}F{Folio}`.

La `Reference/@URI` de cada firma debe apuntar exactamente al identificador
correspondiente. Como cada sobre construido contiene un solo DTE, el ID fijo
del `SetDTE` sigue siendo único dentro del documento XML.

En `Caratula`, los datos de resolución se escriben en el orden exigido por
`EnvioDTE_v10.xsd`: `FchResol`, `NroResol`, `TmstFirmaEnv`. `NroResol` admite
el valor `0` porque su tipo XSD es un entero no negativo.

La raíz declara explícitamente:

```xml
xsi:schemaLocation="http://www.sii.cl/SiiDte EnvioDTE_v10.xsd"
```

La declaración es obligatoria para que el receptor SII identifique el schema
del envío; validar contra un XSD elegido externamente no demuestra que el XML
declare este nombre.

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
- declaración XML exacta en la primera línea;
- `EnvioDTE` comienza en la segunda línea;
- atributos de la raíz en el orden lexical del XML aceptado:
  namespaces, `xsi:schemaLocation` y `version`;
- no se emite el atributo `standalone`;
- nodos de whitespace LF-only e indentación de dos espacios insertados de
  forma determinista antes de firmar;
- `DD` se excluye de esa operación para conservar exactamente los bytes
  cubiertos por `FRMT`;
- sin indentación automática del `Transformer`;
- acceso externo de Transformer deshabilitado.

El layout se fija antes de XMLDSig. No se permite aplicar pretty-print,
normalización de finales de línea ni otra transformación textual sobre el XML
firmado.

Para facturas tipo 33, el constructor contrasta la suma de `Detalle/MontoItem`
con los totales. Si coincide con `MntNeto`, los detalles se emiten como netos.
Si coincide con `MntTotal` y difiere de `MntNeto`, incorpora
`IdDoc/MntBruto=1` antes de firmar. Si no coincide con ninguno, detiene la
construcción con `INCONSISTENT_AMOUNTS`; no firma un DTE tributariamente
ambiguo.

El `Transformer` de JAXP puede ordenar primero los atributos sin namespace. El
normalizador lexical de infraestructura corrige únicamente la etiqueta de
apertura `EnvioDTE`; no cambia `SetDTE`, `DTE`, `Documento`, `DD` ni
`Signature`. El firmador repite esa normalización sobre la salida final y
revalida inmediatamente las dos firmas.

## Límites

El resultado aún no contiene firmas XMLDSig de `Documento` ni `SetDTE`.
Tampoco se ejecuta todavía validación XSD del sobre completo. No se modifican
endpoints, DTOs HTTP ni el esquema de base de datos.
