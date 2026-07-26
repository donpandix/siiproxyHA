# 14. Validador integral de EnvioDTE

## Decisión

`ComprehensiveDteXmlValidatorAdapter` implementa el puerto
`DteXmlValidatorPort` y combina cuatro controles independientes:

1. parsing XML seguro;
2. conformidad con los XSD locales del SII;
3. integridad TED/FRMT;
4. integridad y autorización de las firmas XMLDSig.

`DteXmlSigningService` ejecuta el perfil `ENVIO_DTE` después de firmar
`Documento` y `SetDTE`. Un resultado con severidad `ERROR` impide entregar o
almacenar el artefacto.

## XSD local y resolución cerrada

Se utilizan exclusivamente:

- `EnvioDTE_v10.xsd`;
- `DTE_v10.xsd`;
- `SiiTypes_v10.xsd`;
- `xmldsignature_v10.xsd`.

Los `include` e `import` se resuelven mediante un `LSResourceResolver` limitado
a `/xsd/`. El acceso externo a DTD y esquemas está deshabilitado tanto al
compilar los esquemas como al validar instancias.

El parser rechaza `DOCTYPE`, entidades externas, DTD, XInclude y recursos
externos. El tamaño máximo aceptado por el adaptador es 10 MiB y el reporte XSD
se limita a 100 problemas.

## TED y FRMT

Por cada `TED` se extraen los bytes originales de `DD` directamente del
artefacto recibido. El adaptador:

- exige `FRMT algoritmo="SHA1withRSA"`;
- reconstruye la clave RSA desde `CAF/DA/RSAPK/M` y `E`;
- verifica `FRMT` sobre los bytes exactos de `DD`;
- borra buffers temporales de firma y material RSA.

La firma `FRMA` del CAF no se valida en esta etapa porque requiere una clave o
cadena de confianza oficial del SII que el proyecto todavía no configura.

## XMLDSig y autorización

El perfil `ENVIO_DTE` exige:

- `xsi:schemaLocation` exacto para `EnvioDTE_v10.xsd`;
- un `SetDTE` seguido por su firma;
- cada `DTE` con un `Documento` seguido por su firma;
- IDs presentes y globalmente únicos;
- una referencia interna exacta por firma;
- SHA-1, RSA-SHA1 y C14N inclusiva;
- `KeyInfo` con `KeyValue` y `X509Data`;
- correspondencia entre `KeyValue` y el certificado;
- certificado vigente, RSA y habilitado para firma cuando declara `KeyUsage`;
- RUT del sujeto del certificado igual a `Caratula/RutEnvia`.

Cada firma se valida usando el certificado X.509 incorporado, nunca una clave
proporcionada por el payload fuera de `KeyInfo`. El resolvedor XMLDSig rechaza
URIs diferentes del fragmento esperado.

El perfil `DTE_DOCUMENT` valida un `DTE` autónomo. Como ese artefacto no contiene
`Caratula/RutEnvia`, no puede comprobar autorización del RUT por sí solo.

## Resultado estructurado

Los XML inválidos no provocan excepciones hacia el caso de uso. El puerto
devuelve códigos estables, severidad, mensaje seguro y referencia interna
cuando está disponible. Entre los códigos principales:

- `XML_PARSE`;
- `XML_TOO_LARGE`;
- `XSD_VALIDATION`;
- `SCHEMA_LOCATION`;
- `ID_NOT_UNIQUE`;
- `TED_FRMT_INVALID`;
- `XML_SIGNATURE_INVALID`;
- `SIGNER_AUTHORIZATION`;
- errores de ubicación de `Documento` y `SetDTE`.

## Alternativas evaluadas

Se descartó validar solamente con XSD porque el esquema no comprueba valores
criptográficos. También se descartó reutilizar el certificado resuelto desde
PKCS#12: un validador independiente debe detectar que el certificado publicado
en el XML no coincide con `KeyValue` o con `RutEnvia`.

## Límites

- No se valida aún la cadena de confianza ni revocación X.509.
- No se valida `CAF/FRMA` contra una autoridad oficial del SII.
- No se agregan endpoints ni cambios de OpenAPI, base de datos o migraciones.
