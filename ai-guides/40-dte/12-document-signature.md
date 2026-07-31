# 12. Firma XMLDSig de Documento

## Decisión

El XML construido por `DteXmlAssemblyService` se firma antes de almacenarse o
devolverse. La coordinación queda separada en:

- `DteXmlSigningService`: selecciona la credencial por tenant y
  `RutEnvia`, solicita la firma y registra el uso exitoso;
- `DomXmlSignerAdapter`: abre la credencial mediante el resolvedor PKCS#12,
  aplica XMLDSig y valida los bytes resultantes;
- `DteXmlAssemblyService`: conserva los identificadores de `Documento` y
  `SetDTE` para las etapas posteriores.

La selección usa `RutEnvia`, que identifica al titular autorizado del
certificado. `RutEmisor` continúa representando al contribuyente emisor y no se
reemplaza.

## Perfil de firma

El único perfil habilitado en esta etapa es `SII_LEGACY_RSA_SHA1`:

- referencia interna única `#Documento/@ID`;
- digest SHA-1;
- transform de referencia XMLDSig `enveloped-signature`, igual al XML de
  interoperabilidad aceptado;
- canonicalización inclusiva C14N 1.0 de `SignedInfo`;
- firma RSA-SHA1;
- `KeyInfo` con `KeyValue` seguido por `X509Data`.

`Signature` se inserta como hermano de `Documento`, inmediatamente después,
dentro de `DTE`. Aunque queda fuera del elemento referenciado, el perfil
declara `enveloped-signature` para igualar la estructura interoperable aceptada
por el SII.

La firma se calcula sobre una vista DOM temporal de `Documento` que conserva
elementos, atributos, texto y whitespace, pero no hereda los namespaces
`SiiDte` y `xsi` declarados por `EnvioDTE`. Esta vista reproduce el contexto
lexical del DTE aceptado por el SII. Solamente la `Signature` resultante se
importa al árbol namespace-aware original; el contenido tributario de
`Documento` no se reemplaza ni reserializa por separado.

Antes de firmar se incorpora el separador LF que ubicará `Signature` en una
línea distinta; de este modo el whitespace que afecta al digest de `DTE`
posterior ya es definitivo. Después de firmar solo se normalizan los campos
Base64 de XMLDSig (`SignatureValue`, `Modulus`, `Exponent` y
`X509Certificate`) a líneas de hasta 64 caracteres separadas por LF. No se
emiten CR ni entidades `&#13;`.

El `SignedInfo` materializa explícitamente
`xmlns="http://www.w3.org/2000/09/xmldsig#"` antes de validar la firma. Aunque
el namespace se heredaría de `Signature`, esta representación lexical iguala
el XML aceptado usado como referencia de interoperabilidad.

## Controles de seguridad

Antes de abrir el PKCS#12 se procesa el XML con JAXP seguro y se exige:

- un único `Documento` SII con el `ID` solicitado;
- unicidad global del valor `ID`;
- relación directa `DTE/Documento`;
- ausencia de una firma XMLDSig previa junto al documento;
- ausencia de `DOCTYPE`, DTD, entidades y recursos externos.

La estructura se comprueba nuevamente dentro de la operación que recibe la
clave privada. La resolución de URI se limita a la referencia interna exacta.
La firma interna se valida en la misma vista neutral usada para calcularla
antes de calcular el digest de `SetDTE`. Después de la única serialización
final, `Documento` vuelve a validarse en ese contexto neutral y `SetDTE` sobre
el `EnvioDTE` completo. La excepción de validación segura del JDK se
deshabilita únicamente en esos contextos controlados porque el perfil legado
del SII exige RSA-SHA1.

El contenido `DD` se compara byte a byte antes y después de la firma. Si la
serialización altera el material cubierto por `FRMT`, no se entrega el XML.
Tampoco se aplica pretty-print después de la firma, porque cualquier cambio de
whitespace dentro de `Documento` invalidaría su digest.

La credencial se vuelve a validar al abrirse y su contador de uso se incrementa
solamente después de obtener las dos firmas serializadas y validadas.
Las claves privadas, certificados y contraseñas no atraviesan los puertos de
dominio.

## Límites

- La firma posterior de `SetDTE` se describe en
  `13-setdte-signature.md`.
- La validación integral posterior se describe en
  `14-integral-validation.md`.
- No se valida cadena de confianza ni revocación X.509 en esta etapa.
- No se modifican endpoints, DTOs HTTP, almacenamiento ni base de datos.
