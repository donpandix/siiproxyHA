# Informe técnico: firma XML en `DteSigningService`

## 1. Objetivo y alcance

Este documento describe la implementación observada en
`cl.cesarg.proxysii.service.DteSigningService`, con foco en:

- entrada y precondiciones;
- flujo de firma y validación;
- dependencias internas y externas;
- reglas de negocio y decisiones técnicas;
- estructura del XML firmado de salida;
- separación entre lógica reutilizable, particularidades del proyecto,
  infraestructura y elementos que no conviene copiar literalmente.

El análisis corresponde al código presente en el repositorio. No supone que la
implementación haya sido aceptada por el SII ni reemplaza una prueba contra el
ambiente de certificación.

## 2. Resumen ejecutivo

`DteSigningService` es un servicio Spring sin estado que opera sobre un
`org.w3c.dom.Document` ya construido. Su método `signDte(...)`:

1. localiza por búsqueda manual un elemento con atributo `ID` igual al
   `referenceId`;
2. registra ese atributo como ID en el DOM;
3. calcula una firma XMLDSig sobre ese elemento mediante una referencia
   `#<referenceId>`;
4. usa canonicalización inclusiva, digest SHA-1 y firma RSA-SHA1;
5. incorpora la clave pública y el certificado X.509 en `KeyInfo`;
6. inserta `Signature` como hermano inmediatamente posterior al elemento
   encontrado, dentro de su padre;
7. modifica el DOM recibido y no retorna un XML serializado.

La clase también expone `validateSignature(Document)`. Este método busca **todas**
las firmas XMLDSig del documento, toma la clave pública del certificado
incorporado en el `KeyInfo` de cada firma y devuelve `true` solamente si todas
son criptográficamente válidas.

Hay una separación importante en el proyecto:

- `DteSigningService.signDte(...)` contiene la firma DOM analizada aquí, pero no
  tiene llamadas activas en el código inspeccionado.
- El flujo actual de generación usa
  `DteService.signDocumento(...)`, una implementación paralela que además carga
  el PKCS#12, calcula/inserta la firma TED y serializa el XML.
- `DteSigningService.validateSignature(...)` sí se usa desde
  `PocController` para validar el `EnvioDTE` completo antes de subirlo. Por buscar
  todas las etiquetas `Signature`, en ese punto valida tanto la firma del
  `Documento` como la firma del `SetDTE`.

## 3. Contrato de entrada

### 3.1 Método de firma

```java
void signDte(
    Document doc,
    String referenceId,
    PrivateKey privateKey,
    X509Certificate certificate
) throws Exception
```

| Entrada | Significado | Precondiciones reales |
|---|---|---|
| `doc` | DOM XML que se modificará in situ | Debe ser no nulo, estar habilitado para namespaces y contener un elemento con atributo literal `ID` igual a `referenceId`. |
| `referenceId` | Identificador del nodo firmado | Se usa sin normalización ni escape para construir `URI="#<referenceId>"`. Debe coincidir exactamente con el valor de un atributo `ID`. |
| `privateKey` | Clave privada con la que se genera `SignatureValue` | Debe ser utilizable con RSA-SHA1. La clase no la carga, no verifica su origen y no comprueba anticipadamente que corresponda al certificado. |
| `certificate` | Certificado X.509 publicado en `KeyInfo` | Debe ser no nulo y exponer una clave pública compatible. No se comprueban vigencia, cadena de confianza, revocación ni uso de clave. |

Precondiciones implícitas adicionales:

- El elemento encontrado debería ser el `<Documento>` del DTE y su padre debería
  ser `<DTE>`, pero la implementación no comprueba nombres ni namespaces.
- El atributo debe llamarse exactamente `ID`, respetando mayúsculas.
- El ID debería ser único en todo el DOM. La búsqueda manual toma la primera
  coincidencia y no detecta duplicados.
- El DOM no debería contener ya una firma para el mismo `Documento`; el método
  no es idempotente y puede insertar una segunda firma.
- Todo contenido de `<Documento>` que deba quedar protegido debe estar completo
  antes de invocar la firma. Cualquier cambio posterior invalida el digest.

### 3.2 Método de validación

```java
boolean validateSignature(Document doc)
```

Recibe un DOM potencialmente firmado y devuelve:

- `true`: existe al menos una etiqueta `Signature` en el namespace XMLDSig y
  todas las firmas encontradas pasan `XMLSignature.validate(...)`;
- `false`: no hay firmas, alguna firma no valida o se produce cualquier
  excepción durante el proceso.

El método no devuelve detalles estructurados del fallo. Los diagnósticos se
escriben directamente en `System.err`.

## 4. Flujo de ejecución de `signDte`

### 4.1 Construcción de la referencia

Se obtiene un `XMLSignatureFactory` para el mecanismo `DOM` y se crea una única
referencia:

```text
URI             = "#" + referenceId
DigestMethod    = http://www.w3.org/2000/09/xmldsig#sha1
Transform       = http://www.w3.org/TR/2001/REC-xml-c14n-20010315
```

La transformación aplicada al recurso referenciado es canonicalización XML
inclusiva (C14N 1.0). No se usa `enveloped-signature`. En esta estructura no es
necesaria para excluir la firma, porque `Signature` queda fuera de
`Documento`, que es el nodo digerido.

### 4.2 Construcción de `SignedInfo`

`SignedInfo` contiene:

- canonicalización inclusiva C14N 1.0;
- algoritmo de firma RSA-SHA1;
- exactamente una referencia, la construida en el paso anterior.

La implementación delega al proveedor XMLDSig del JDK el cálculo de:

- `DigestValue`: SHA-1 del `<Documento>` después de aplicar el transform;
- `SignatureValue`: firma RSA-SHA1 del `SignedInfo` canonicalizado.

### 4.3 Construcción de `KeyInfo`

El `KeyInfo` se construye en este orden:

1. `KeyValue`, obtenido desde `certificate.getPublicKey()`;
2. `X509Data`, con el certificado X.509 completo.

Para un certificado RSA, el XML resultante normalmente materializa el
`KeyValue` como `RSAKeyValue/Modulus/Exponent`. `X509Certificate` contiene el
certificado DER codificado en Base64.

El orden coincide con el esquema `xmldsignature_v10.xsd` incluido en el
proyecto, que exige primero `KeyValue` y después `X509Data`.

### 4.4 Resolución y registro del ID

`getElementById(...)` no utiliza `Document.getElementById(...)`. Recorre todos
los elementos de `doc` con `getElementsByTagName("*")` y devuelve el primero
cuyo atributo `ID` coincida.

Si no encuentra el elemento, lanza:

```text
RuntimeException: No se encontró el elemento con ID: <referenceId>
```

Cuando lo encuentra, ejecuta:

```java
documentoElement.setIdAttribute("ID", true);
```

Este registro es necesario para que el resolvedor de XMLDSig pueda convertir la
URI fragmentaria `#<referenceId>` en el nodo DOM firmado cuando el XML no fue
validado previamente contra un DTD/XSD.

### 4.5 Ubicación de la firma

El padre del elemento encontrado se trata como el `<DTE>`:

```java
Element dteElement = (Element) documentoElement.getParentNode();
```

El contexto de firma usa ese padre como contenedor y el hermano siguiente de
`Documento` como punto de inserción. Por lo tanto:

- si `Documento` tiene un hermano siguiente, `Signature` se inserta antes de
  ese nodo;
- si no lo tiene, se agrega al final del padre.

El resultado esperado es:

```xml
<DTE>
  <Documento ID="...">...</Documento>
  <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">...</Signature>
</DTE>
```

Esta ubicación coincide con `DTE_v10.xsd`, cuya secuencia exige una alternativa
de documento tributario seguida por `ds:Signature`.

### 4.6 Mutación y salida

`signature.sign(dsc)` agrega los nodos XMLDSig al mismo DOM recibido. El método:

- no crea una copia;
- no devuelve `Document`;
- no serializa a `String`, archivo ni stream;
- no define encoding, indentación ni declaración XML;
- no valida el XSD después de firmar;
- no ejecuta automáticamente `validateSignature(...)`.

El consumidor es responsable de serializar el DOM sin modificar el contenido
firmado de una forma que cambie su representación lógica.

## 5. Flujo de ejecución de `validateSignature`

1. Busca todas las etiquetas `{http://www.w3.org/2000/09/xmldsig#}Signature`.
2. Si no encuentra ninguna, informa el problema por `System.err` y devuelve
   `false`.
3. Para cada firma:
   1. obtiene el `URI` del primer `Reference` descendiente;
   2. si el URI comienza por `#`, busca manualmente el elemento con ese `ID` y
      registra el atributo como tipo ID;
   3. crea un `DOMValidateContext` con un `KeySelector` propio;
   4. el selector toma el primer `X509Certificate` presente en `X509Data` y
      devuelve su clave pública;
   5. deshabilita `org.jcp.xml.dsig.secureValidation` para permitir RSA-SHA1;
   6. deserializa la firma y ejecuta la validación criptográfica completa;
   7. si falla, valida por separado `SignatureValue` y cada `Reference` para
      imprimir diagnóstico.
4. Retorna `true` sólo si todas las firmas encontradas son válidas.
5. Ante cualquier excepción, imprime el stack trace y retorna `false`.

### Alcance real de esa validación

La validación demuestra integridad matemática respecto de la clave pública
contenida en la propia firma. **No demuestra por sí sola la identidad o
confiabilidad del firmante**, porque:

- confía en el certificado incluido por el mismo XML;
- no valida la cadena contra una autoridad certificadora confiable;
- no comprueba vigencia;
- no consulta revocación;
- no aplica una política de certificado del SII;
- no comprueba que la identidad del certificado corresponda al emisor o
  firmante autorizado del DTE.

En el `PocController`, el método se aplica al `EnvioDTE` completo. Como recorre
todas las firmas del DOM, su responsabilidad efectiva es más amplia que
“validar la firma de un DTE”.

## 6. Reglas de negocio y restricciones observadas

### 6.1 Reglas del formato DTE/SII implementadas

- El objeto protegido es el elemento identificado por `Documento/@ID`.
- La referencia es interna al mismo XML y usa `URI="#ID"`.
- El `ID` es obligatorio según el XSD local y debe poder resolverse como tipo
  XML ID.
- La firma se ubica como hermana de `Documento` y dentro de `DTE`.
- `DTE/@version` pertenece al documento de entrada; la clase no lo crea ni
  valida.
- El digest es SHA-1.
- La firma es RSA-SHA1.
- La canonicalización de `SignedInfo` es C14N 1.0 inclusiva.
- La referencia aplica un transform C14N 1.0 inclusivo.
- `KeyInfo` publica tanto `KeyValue` como `X509Data/X509Certificate`, en ese
  orden.

Los algoritmos anteriores coinciden con las restricciones del
`xmldsignature_v10.xsd` local: C14N inclusiva, digest SHA-1 y firma RSA-SHA1 o
DSA-SHA1. La clase elige específicamente RSA-SHA1.

### 6.2 Reglas implícitas no comprobadas por el código

- El elemento localizado realmente debe ser un `Documento` del namespace
  `http://www.sii.cl/SiiDte`.
- Su padre realmente debe ser `DTE`.
- El `referenceId` debe ser único.
- La clave privada debe corresponder a la clave pública del certificado.
- El certificado debe ser RSA, vigente, confiable y autorizado para firmar.
- El XML debe cumplir los XSD del SII antes y después de firmarse.
- La hora `TmstFirma`, el TED/FRMT y el CAF ya deben estar correctos antes de la
  firma; esta clase no los genera ni los valida.
- La codificación final requerida por el flujo del proyecto
  (`ISO-8859-1`) debe aplicarse al serializar; no forma parte de esta clase.

## 7. XML firmado de salida

La salida no es un `String`: es el mismo `Document` de entrada, enriquecido con
un bloque XMLDSig. La forma lógica esperada es la siguiente; los valores entre
corchetes son ilustrativos y dependen del documento, clave, certificado y
proveedor JDK:

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<DTE xmlns="http://www.sii.cl/SiiDte" version="1.0">
  <Documento ID="[referenceId]">
    <!-- Encabezado, Detalle, TED, TmstFirma y demás contenido previo -->
  </Documento>

  <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">
    <SignedInfo>
      <CanonicalizationMethod
          Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
      <SignatureMethod
          Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
      <Reference URI="#[referenceId]">
        <Transforms>
          <Transform
              Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
        </Transforms>
        <DigestMethod
            Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
        <DigestValue>[SHA-1 en Base64]</DigestValue>
      </Reference>
    </SignedInfo>
    <SignatureValue>[firma RSA-SHA1 en Base64]</SignatureValue>
    <KeyInfo>
      <KeyValue>
        <RSAKeyValue>
          <Modulus>[módulo RSA en Base64]</Modulus>
          <Exponent>[exponente RSA en Base64]</Exponent>
        </RSAKeyValue>
      </KeyValue>
      <X509Data>
        <X509Certificate>[certificado DER en Base64]</X509Certificate>
      </X509Data>
    </KeyInfo>
  </Signature>
</DTE>
```

El proveedor XMLDSig puede elegir prefijos (`ds:Signature`) o namespace por
defecto y puede introducir saltos de línea en datos Base64. Esas diferencias
superficiales no cambian el modelo de nodos, pero cualquier requisito externo
sobre serialización exacta debe probarse con el JDK y el receptor reales.

### Qué queda protegido

El `DigestValue` protege el contenido completo del elemento referenciado
`Documento`, incluidos sus descendientes y atributos conforme al resultado de
la canonicalización. No protege:

- atributos o nodos de `DTE` que estén fuera de `Documento`;
- la propia etiqueta `Signature`;
- otros DTE o el `SetDTE` de un sobre.

La firma del sobre `SetDTE` es una operación diferente, implementada en
`EnvioDteService`.

## 8. Dependencias

### 8.1 Dependencias internas del proyecto

`DteSigningService` no invoca otros servicios, repositorios ni utilidades del
proyecto. Sus únicas relaciones internas observadas son:

- registro como bean mediante `@Service`;
- inyección en `PocController`;
- uso de `validateSignature(...)` antes de subir un `EnvioDTE`;
- alineación conceptual con los XSD locales:
  `DTE_v10.xsd` y `xmldsignature_v10.xsd`.

La constante `SII_NAMESPACE` está declarada, pero no se usa para localizar ni
validar los elementos.

No hay pruebas unitarias directas para `DteSigningService` en el repositorio
inspeccionado.

### 8.2 Dependencias externas de ejecución

| Dependencia | Uso |
|---|---|
| Java XML Digital Signature API (`javax.xml.crypto`, JSR 105) | Construcción, firma, deserialización y validación de XMLDSig. |
| Proveedor XMLDSig del JDK (`XMLSignatureFactory` con mecanismo `DOM`) | Implementación criptográfica y materialización de nodos XML. |
| Java Security/JCA | Tipos `PrivateKey`, `PublicKey` y `X509Certificate`; algoritmos RSA/SHA-1. |
| W3C DOM (`org.w3c.dom`) | Documento mutable, búsqueda de elementos e inserción de la firma. |
| Spring Framework | Descubrimiento de la clase como servicio mediante `@Service`. |

No se observa una biblioteca XMLDSig adicional en `pom.xml`; la funcionalidad
criptográfica proviene del JDK. El proyecto declara Java 21 y Spring Boot
3.5.10.

### 8.3 Infraestructura y configuración fuera de la clase

La clase no:

- lee archivos PKCS#12;
- conoce rutas o contraseñas;
- accede a variables de entorno;
- llama servicios del SII;
- escribe archivos;
- serializa XML;
- carga o ejecuta los XSD.

En el flujo actual del proyecto, las rutas y contraseñas del certificado se
obtienen desde `src/main/resources/application-develop.properties`, archivo
ignorado por Git. Esa configuración corresponde al controlador y a los otros
servicios de firma, no al contrato de `DteSigningService`.

Los XSD bajo `src/main/resources/schema/` son referencias locales para
validación, pero `DteSigningService` no los consulta en tiempo de ejecución.

## 9. Clasificación para una migración

### 9.1 Lógica de negocio reutilizable

Puede trasladarse como concepto, con una interfaz más explícita:

- firmar un elemento por una referencia fragmentaria a un ID;
- registrar `ID` en el DOM antes de resolver la referencia;
- construir `SignedInfo`, `Reference` y `KeyInfo`;
- insertar la firma fuera del nodo protegido cuando el esquema lo exige;
- separar la carga de credenciales de la operación criptográfica;
- validar `SignatureValue` y cada `Reference`;
- devolver un resultado de validación con detalle.

También es reutilizable la idea de recibir `PrivateKey` y `X509Certificate` ya
resueltos, porque desacopla la lógica de firma del almacenamiento de secretos.

### 9.2 Código específico de este proyecto

- El paquete `cl.cesarg.proxysii.service`.
- La anotación y forma de inyección Spring elegidas por este POC.
- El uso del validador desde `PocController` sobre el `EnvioDTE` completo.
- La convivencia con implementaciones paralelas en `DteService`,
  `EnvioDteService` y `XmlSignatureService`.
- La lectura de `application-develop.properties`.
- Las rutas `output/` y `local-secrets/`.
- El supuesto del flujo POC de que un mismo contexto contiene firmas de
  `Documento` y `SetDTE`.
- Los mensajes a `System.err` y el manejo genérico de errores.

### 9.3 Infraestructura o configuración

- Fuente segura del PKCS#12, contraseña y selección del alias.
- Gestión de secretos y rotación de certificados.
- Proveedor criptográfico disponible en la JVM.
- Políticas de seguridad de Java que restringen SHA-1.
- Parser XML seguro y habilitado para namespaces.
- Serialización final, encoding ISO-8859-1 y política de saltos de línea Base64.
- Validación XSD.
- Truststore, autoridades certificadoras y verificación de revocación.
- Logs, métricas, trazabilidad y protección de datos sensibles.
- Pruebas contra el ambiente de certificación del SII.

### 9.4 Elementos que no deberían copiarse literalmente

1. **Búsqueda global por cualquier atributo `ID`.** Debe limitarse al elemento y
   namespace esperados, exigir unicidad y rechazar duplicados.
2. **Cast del padre sin validación.** Conviene comprobar explícitamente que el
   nodo es `Documento` y el padre es `DTE`.
3. **Confianza en el certificado embebido.** Sirve para verificar consistencia
   criptográfica, no autenticidad. Una migración debe aplicar una política de
   confianza y autorización.
4. **Desactivar la validación segura como solución genérica.** Aunque la
   propiedad se aplica al contexto de validación concreto,
   `secureValidation=false` reduce defensas. Si el perfil legado del SII obliga
   a RSA-SHA1, la excepción debe quedar confinada a ese perfil y acompañada de
   controles compensatorios.
5. **RSA-SHA1/SHA-1 fuera del contexto SII legado.** Son algoritmos obsoletos.
   No deben convertirse en el valor por defecto de una librería reutilizable.
6. **`throws Exception` y `RuntimeException` genéricos.** Es preferible distinguir
   documento inválido, credencial inválida, algoritmo no disponible y fallo de
   firma.
7. **Salida de diagnóstico por `System.err` y `printStackTrace`.** Debe
   reemplazarse por logging estructurado o un objeto de resultado sin exponer
   material sensible.
8. **Retorno booleano sin evidencia.** Conviene devolver por firma el URI, estado
   de `SignatureValue`, estado de cada referencia y estado de confianza del
   certificado.
9. **Ausencia de controles del certificado.** Deben validarse correspondencia
   clave-certificado, tipo de clave, vigencia, key usage, cadena y autorización
   del firmante.
10. **No idempotencia silenciosa.** Debe definirse si una firma existente se
    rechaza, reemplaza o conserva.
11. **Dependencia del DOM mutable aportado por el llamador.** Es necesario
    documentar propiedad y ciclo de vida o devolver una copia/resultado
    inmutable.
12. **No validar el XML contra XSD.** La validez criptográfica y la validez
    estructural son controles distintos; ambos deben ejecutarse.
13. **Usar sólo el primer `Reference` para preparar IDs.** Una implementación
    genérica debe procesar y restringir todas las referencias antes de validar.
14. **Aceptar cualquier firma encontrada en cualquier nivel.** Debe verificarse
    número, ubicación, objetivo y relación entre firma y tipo de documento.
15. **Copiar la constante `SII_NAMESPACE` sin usarla.** En una migración debería
    emplearse para validar los nodos o eliminarse.

## 10. Riesgos y brechas relevantes

| Prioridad | Hallazgo | Consecuencia |
|---|---|---|
| Alta | El certificado se toma del mismo XML sin validar confianza ni identidad. | Un atacante puede crear su propio certificado y una firma matemáticamente válida; el método podría retornar `true`. |
| Alta | No se valida que el ID sea único ni que apunte a `Documento` dentro de `DTE`. | Riesgo de validar o firmar un nodo distinto al esperado, además de ambigüedad ante IDs duplicados. |
| Alta | El método `signDte(...)` no participa en el flujo actual y carece de pruebas directas. | Migrar esta clase literalmente puede trasladar código no ejercitado en producción/POC. |
| Media | La validación deshabilita las restricciones seguras de XMLDSig. | Se aceptan algoritmos considerados inseguros por Java; necesario sólo por compatibilidad controlada con el perfil legado. |
| Media | No se controla existencia previa, cantidad ni ubicación de firmas. | Puede producirse un XML con firmas duplicadas o una validación semánticamente incorrecta. |
| Media | No hay validación XSD integrada. | Una firma válida puede envolver un XML que el SII rechace estructuralmente. |
| Media | No se verifica correspondencia entre clave privada y certificado antes de firmar. | Se puede generar una firma cuyo `KeyInfo` no permita validarla. |
| Baja | Los errores se reducen a excepción genérica o booleano. | Diagnóstico y observabilidad insuficientes durante una migración. |

## 11. Recomendación de extracción

Para migrar la capacidad sin arrastrar particularidades del POC, conviene
separarla en cuatro componentes:

1. **Política DTE:** define namespace, elemento firmable, ubicación de
   `Signature`, algoritmos exigidos y cardinalidad.
2. **Firmador XMLDSig:** recibe DOM, ID, clave y certificado; valida
   precondiciones; firma; retorna un resultado explícito.
3. **Proveedor de credenciales:** carga PKCS#12/HSM/secret manager, selecciona
   alias y valida el certificado.
4. **Validador:** combina validación XSD, integridad XMLDSig, confianza X.509,
   autorización del firmante y reglas semánticas de ubicación/referencia.

La firma TED (`FRMT` sobre `DD`) y la firma del sobre (`SetDTE`) deben conservarse
como casos de uso separados: protegen nodos distintos y tienen contratos
distintos, aunque reutilicen primitivas criptográficas.

## 12. Verificación realizada para este informe

Se inspeccionaron:

- `DteSigningService`;
- sus referencias desde `PocController`;
- las implementaciones relacionadas `DteService`, `EnvioDteService`,
  `XmlSignatureService` y utilidades de validación;
- los tests disponibles;
- `pom.xml`;
- `DTE_v10.xsd`, `EnvioDTE_v10.xsd` y `xmldsignature_v10.xsd`;
- configuración y exclusiones relacionadas con certificados y archivos de
  salida.

No se ejecutó una firma real con credenciales ni una prueba contra el SII. El
repositorio no contiene una prueba directa de `DteSigningService.signDte(...)`;
por eso la estructura de salida mostrada es la forma lógica producida por JSR
105, no una captura certificada de aceptación por el SII.
