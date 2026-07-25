# 13. Firma XMLDSig de SetDTE

## Decisión

`DteXmlSigningService` ejecuta una cadena de dos firmas con una única
credencial seleccionada por tenant y `RutEnvia`:

1. firma `Documento`;
2. usa esos bytes como entrada para firmar `SetDTE`;
3. entrega solamente el XML donde ambas firmas fueron revalidadas.

La firma de `SetDTE` queda como hermana inmediatamente posterior a `SetDTE`
dentro de `EnvioDTE`. Como `SetDTE` contiene cada `DTE`, `Documento` y su firma,
el digest del sobre protege también las firmas internas.

## Perfil criptográfico

Se reutiliza el perfil explícito `SII_LEGACY_RSA_SHA1`:

- referencia interna única `#SetDTE/@ID`;
- digest SHA-1;
- canonicalización inclusiva C14N 1.0 para la referencia y `SignedInfo`;
- firma RSA-SHA1;
- `KeyInfo` ordenado como `KeyValue`, `X509Data`.

No se usa `enveloped-signature`: la firma es hermana del nodo referenciado y no
forma parte de su digest.

## Validaciones encadenadas

Antes de abrir la credencial para firmar el sobre se exige:

- un único `SetDTE` SII con el ID solicitado;
- relación directa `EnvioDTE/SetDTE`;
- ausencia de una firma previa junto a `SetDTE`;
- al menos un `DTE`;
- exactamente un `Documento` seguido por una firma en cada `DTE`;
- IDs de `Documento` presentes y globalmente únicos.

Dentro de la operación PKCS#12 se valida criptográficamente cada firma de
`Documento` con la misma credencial seleccionada. Después de firmar `SetDTE`,
el XML se serializa y se vuelve a parsear para validar:

- todas las firmas internas de `Documento`;
- la firma externa de `SetDTE`;
- referencias internas exactas;
- algoritmos exactos del perfil legado;
- posición estructural de cada firma;
- conservación byte a byte de `DD`.

La resolución de URI rechaza referencias distintas de la esperada y todo el
parsing mantiene deshabilitados `DOCTYPE`, DTD, entidades y accesos externos.

## Registro de uso

La credencial se selecciona una sola vez y se vuelve a abrir de forma segura en
cada operación criptográfica. Cada firma incrementa el contador solamente
después de ser válida:

- si falla `Documento`, no se registra uso;
- si `Documento` es válido pero falla `SetDTE`, se registra solamente la
  operación de `Documento`;
- si ambas son válidas, se registran dos operaciones.

## Alternativas evaluadas

Se descartó firmar ambos nodos en una única manipulación DOM porque dificulta
comprobar el artefacto intermedio y registrar qué operación falló. También se
descartó seleccionar nuevamente la credencial para el sobre, ya que un cambio
concurrente del certificado predeterminado podría producir firmas con
identidades distintas.

## Límites

- La validación integral posterior se describe en
  `14-integral-validation.md`.
- No se valida cadena de confianza ni revocación X.509.
- No se modifican endpoints, DTOs HTTP, almacenamiento ni base de datos.
