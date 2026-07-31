# 7. Contratos de firma y credenciales

## Decisión

La orquestación de `application` debe trabajar con bytes XML, identificadores y
descriptores seguros. Los puertos no exponen `PrivateKey`, `X509Certificate`,
DOM, rutas de MinIO, contraseñas cifradas ni detalles del proveedor.

Contratos iniciales:

- `XmlSignerPort`: firma un elemento aislado o encadena `Documento` y `SetDTE`
  sobre el mismo árbol mediante un perfil explícito.
- `SigningCredentialPort`: selecciona una credencial firmante por tenant y RUT.
- `CafMaterialPort`: selecciona el CAF que autoriza un folio sin exponer `RSASK`.
- `DteXmlValidatorPort`: devuelve problemas estructurados de validación.

El único perfil inicial es `SII_LEGACY_RSA_SHA1`. Mantenerlo explícito evita
convertir SHA-1 en el algoritmo general de la aplicación.

## Alternativas evaluadas

1. Entregar `PrivateKey`, `X509Certificate` y `Document` a `application`.
   Se descarta porque acopla la orquestación a JCA, DOM y al ciclo de vida de
   secretos.
2. Entregar un callback o handle opaco con la clave abierta.
   Se descarta inicialmente porque complica propiedad, cierre y concurrencia del
   material sensible.
3. Resolver descriptores seguros y pasar el ID de credencial al firmador.
   Es la opción elegida. El adaptador de infraestructura abrirá y volverá a
   validar la credencial inmediatamente antes de firmar.

## Límites

- Estos contratos no implementan PKCS#12, XMLDSig, TED/FRMT ni validación XSD.
- No cambian endpoints, DTOs HTTP, base de datos ni almacenamiento.
- La selección no autoriza por sí sola la firma: el adaptador debe revalidar
  tenant, RUT, estado, vigencia y capacidad de clave privada al ejecutar.
- Los arrays de bytes se copian defensivamente para impedir mutaciones externas.
- El contrato encadenado recibe ambos IDs y retorna solo el `EnvioDTE` final;
  no expone el DOM ni un artefacto intermedio susceptible de reserialización.

## Compatibilidad de paquetes

El código actual usa el paquete raíz `cl.cesarg.siiproxyHA`. Aunque algunas
guías antiguas muestran `cl.cesarg.siiproxyha`, estos contratos conservan el
paquete efectivo para evitar crear una jerarquía paralela o romper imports.

## Riesgo y rollback

Existe una ventana entre seleccionar una credencial y ejecutar la firma. La
implementación deberá volver a comprobar su estado usando `credentialId`.

El rollback consiste en retirar los contratos mientras no tengan adaptadores ni
consumidores. No hay migraciones ni datos que revertir.
