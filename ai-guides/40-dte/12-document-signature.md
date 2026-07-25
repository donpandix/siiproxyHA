# 12. Firma XMLDSig de Documento

## Decisión

El XML construido por `DteXmlAssemblyService` se firma antes de almacenarse o
devolverse. La coordinación queda separada en:

- `DteDocumentSigningService`: selecciona la credencial por tenant y
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
- canonicalización inclusiva C14N 1.0 como transform de la referencia;
- canonicalización inclusiva C14N 1.0 de `SignedInfo`;
- firma RSA-SHA1;
- `KeyInfo` con `KeyValue` seguido por `X509Data`.

`Signature` se inserta como hermano de `Documento`, inmediatamente después,
dentro de `DTE`. No se usa el transform `enveloped-signature` porque la firma
queda fuera del elemento referenciado.

## Controles de seguridad

Antes de abrir el PKCS#12 se procesa el XML con JAXP seguro y se exige:

- un único `Documento` SII con el `ID` solicitado;
- unicidad global del valor `ID`;
- relación directa `DTE/Documento`;
- ausencia de una firma XMLDSig previa junto al documento;
- ausencia de `DOCTYPE`, DTD, entidades y recursos externos.

La estructura se comprueba nuevamente dentro de la operación que recibe la
clave privada. La resolución de URI se limita a la referencia interna exacta.
La validación criptográfica se ejecuta sobre una nueva lectura de los bytes
serializados. La excepción de validación segura del JDK se deshabilita
únicamente en ese contexto controlado porque el perfil legado del SII exige
RSA-SHA1.

El contenido `DD` se compara byte a byte antes y después de la firma. Si la
serialización altera el material cubierto por `FRMT`, no se entrega el XML.

La credencial se vuelve a validar al abrirse y su contador de uso se incrementa
solamente después de obtener una firma serializada criptográficamente válida.
Las claves privadas, certificados y contraseñas no atraviesan los puertos de
dominio.

## Límites

- `SetDTE` todavía no se firma; el adaptador rechaza ese target explícitamente.
- Aún no se ejecuta validación XSD del sobre completo.
- No se valida cadena de confianza ni revocación X.509 en esta etapa.
- No se modifican endpoints, DTOs HTTP, almacenamiento ni base de datos.
