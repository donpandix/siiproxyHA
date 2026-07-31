# 13. Firma XMLDSig de SetDTE

## Decisión

`DteXmlSigningService` ejecuta una cadena de dos firmas con una única
credencial seleccionada por tenant y `RutEnvia`:

1. firma `Documento`;
2. importa y normaliza la firma interna en el DOM original;
3. firma `SetDTE` sin reserializar nuevamente `Documento`;
4. serializa una sola vez y entrega únicamente el XML donde ambas firmas fueron
   revalidadas.

Para la primera etapa, el adaptador construye una vista temporal de
`Documento` sin los namespaces heredados del sobre. Esta vista solo determina
`DigestValue` y `SignatureValue`; la firma terminada se inserta como hermana de
`Documento` en el árbol original. La segunda etapa continúa operando sobre el
`SetDTE` namespace-aware completo y cubre la firma interna ya finalizada.

La serialización final conserva el orden estructural del XSD y normaliza
solamente el orden lexical de los atributos de `EnvioDTE` para coincidir con el
ejemplo aceptado. Como la raíz no forma parte de las referencias `Documento` ni
`SetDTE`, este ajuste no altera sus digests; de todos modos ambos se recalculan
y validan sobre los bytes definitivos.

La firma de `SetDTE` queda como hermana inmediatamente posterior a `SetDTE`
dentro de `EnvioDTE`. Como `SetDTE` contiene cada `DTE`, `Documento` y su firma,
el digest del sobre protege también las firmas internas.

## Perfil criptográfico

Se reutiliza el perfil explícito `SII_LEGACY_RSA_SHA1`:

- referencia interna única `#SetDTE/@ID`;
- digest SHA-1;
- transform de referencia XMLDSig `enveloped-signature`;
- canonicalización inclusiva C14N 1.0 para `SignedInfo`;
- firma RSA-SHA1;
- `KeyInfo` ordenado como `KeyValue`, `X509Data`.

La firma es hermana del nodo referenciado y no forma parte de su digest. El
transform `enveloped-signature` se declara para mantener el mismo perfil
estructural del XML aceptado usado como referencia de interoperabilidad.

El separador LF anterior a cada `Signature` se incorpora antes de calcular la
firma correspondiente. Los valores Base64 se serializan con líneas de hasta 64
caracteres y LF-only, sin CR ni `&#13;`. Al terminar la firma de `SetDTE`, los
bytes quedan definitivos: no se reindenta ni se normaliza el XML.

El ancho interoperable de los valores Base64 es de 64 caracteres. Durante la
serialización final, el `SignedInfo` del sobre materializa explícitamente tanto
el namespace XMLDSig como
`xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"`, igual que el XML
aceptado de referencia. El validador vuelve a parsear esos bytes finales y
comprueba las firmas de `Documento` y `SetDTE`; después de esa validación no se
realiza ninguna modificación.

## Validaciones encadenadas

Antes de abrir la credencial para firmar el sobre se exige:

- un único `SetDTE` SII con el ID solicitado;
- relación directa `EnvioDTE/SetDTE`;
- ausencia de una firma previa junto a `SetDTE`;
- al menos un `DTE`;
- exactamente un `Documento` seguido por una firma en cada `DTE`;
- IDs de `Documento` presentes y globalmente únicos.

Dentro de una única operación PKCS#12 se firma y valida `Documento` en su vista
lexical neutral, se importa la firma terminada y luego se firma `SetDTE` sobre
el árbol completo. Después se realiza la única serialización del flujo y se
vuelve a parsear para validar:

- todas las firmas internas de `Documento` en contexto neutral;
- la firma externa de `SetDTE`;
- referencias internas exactas;
- algoritmos exactos del perfil legado;
- posición estructural de cada firma;
- conservación byte a byte de `DD`.

La resolución de URI rechaza referencias distintas de la esperada y todo el
parsing mantiene deshabilitados `DOCTYPE`, DTD, entidades y accesos externos.

## Registro de uso

La credencial se selecciona y abre una sola vez. Las dos operaciones se
registran únicamente después de que la serialización final supera la validación
integral. Si falla cualquiera de las firmas o el artefacto final, no se registra
uso parcial.

## Alternativas evaluadas

Se eligió mantener un único DOM final y una única serialización del
`EnvioDTE`. La vista neutral de `Documento` es transitoria y no reemplaza el
subárbol original; solo aporta la `Signature` compatible con el contexto
lexical observado en el SII. La firma interna se valida antes de firmar el
sobre y nuevamente desde los bytes finales. También se descartó seleccionar
otra credencial para el sobre, ya que podría producir firmas con identidades
distintas.

## Límites

- La validación integral posterior se describe en
  `14-integral-validation.md`.
- No se valida cadena de confianza ni revocación X.509.
- No se modifican endpoints, DTOs HTTP, almacenamiento ni base de datos.
