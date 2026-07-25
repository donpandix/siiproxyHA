# 8. Resolución segura de PKCS#12

## Decisión

Una credencial solo es apta para firma cuando el archivo almacenado es `.p12`
o `.pfx` y contiene exactamente una entrada `PrivateKey` RSA acompañada por un
certificado X.509 válido.

La capacidad se comprueba al resolver la credencial. No se agrega todavía una
columna `signing_capable`, evitando una migración y un backfill prematuros.

## Controles

- Selección por tenant, RUT normalizado, estado activo y vigencia.
- Soporte exclusivo de PKCS#12/PFX para firma.
- Contraseña descifrada únicamente durante la operación.
- Validación de metadatos persistidos contra el certificado contenido.
- Validación de `digitalSignature` cuando `KeyUsage` está presente.
- Verificación criptográfica de correspondencia entre clave privada y pública
  mediante un desafío RSA-SHA256 interno.
- Limpieza de los bytes descargados y del arreglo de contraseña al finalizar.
- Rechazo de almacenes sin clave privada o con múltiples entradas privadas.
- Contabilización atómica de uso separada de la resolución.

Los certificados `.cer`, `.crt` y PEM pueden permanecer registrados para
consulta, pero no se seleccionan como credenciales firmantes.

## Ciclo de vida

`Pkcs12SigningCredentialResolver` ejecuta una operación callback dentro del
ámbito donde la clave está abierta. La clave JCA nunca cruza hacia `application`
ni se almacena en DTOs.

El futuro adaptador XMLDSig debe:

1. Resolver el descriptor seguro.
2. Reabrir y revalidar la credencial por ID, tenant y RUT.
3. Firmar dentro del callback.
4. Registrar uso solo después de obtener una firma válida.

## Riesgo residual

`CryptoService` actualmente devuelve la contraseña descifrada como `String`;
ese objeto no puede borrarse de memoria de forma determinista. El resolver
reduce su alcance y limpia la copia `char[]`. Una evolución posterior puede
incorporar una API de descifrado hacia buffers borrables o un proveedor KMS/HSM.
