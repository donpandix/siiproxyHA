Postman collection and quick curl examples

Import the collection at documents/postman/siiproxyha-collection.json into Postman.

Environment variables available in documents/postman/siiproxyha-environment.json:
- `baseUrl` (e.g. http://localhost:8080)
- `tenantId`, `receptorId`, `cafId`, `rutEnvia`

Quick curl examples:

# Tenants
Create:
```
curl -X POST -H "Content-Type: application/json" -d '{"tenantCode":"acme","rutEmisor":"76184688-4","razonSocial":"ACME"}' {{baseUrl}}/api/v1/tenants
```

List:
```
curl {{baseUrl}}/api/v1/tenants
```

# Receptores
Create (for tenant):
```
curl -X POST -H "Content-Type: application/json" -d '{"rutReceptor":"22222222-2","razonSocial":"Cliente Uno"}' {{baseUrl}}/api/v1/tenants/<TENANT_ID>/receptores
```

List:
```
curl {{baseUrl}}/api/v1/tenants/<TENANT_ID>/receptores
```

# CAF
Upload de fixture sanitizado (sirve para probar registro y asignación de folios,
pero no contiene `RSASK` y no permite generar la firma `FRMT`):

```
curl -v -F "tenantId=<TENANT_ID>" -F "file=@documents/samples/FoliosSII_33.xml" {{baseUrl}}/api/v1/caf
```

Para una prueba local de firma, guarde el CAF real bajo `local-secrets/` y nunca
lo agregue a Git:

```
curl -v -F "tenantId=<TENANT_ID>" -F "file=@local-secrets/FoliosSII_33.xml" {{baseUrl}}/api/v1/caf
```

Download:
```
curl -L -o caf.xml {{baseUrl}}/api/v1/caf/<CAF_ID>/download
```

# Health
```
curl {{baseUrl}}/api/v1/health
```
# Postman — siiproxyHA

Importar los siguientes archivos en Postman:

- `documents/postman/siiproxyha-collection.json` (colección de llamadas)
- `documents/postman/siiproxyha-environment.json` (entorno local)

Pasos rápidos:

1. Arrancar infraestructura local: `docker compose up -d` (Postgres, MinIO, LocalStack si aplica).
2. Ejecutar la aplicación: `./mvnw spring-boot:run`.
3. Importar el environment y seleccionar `siiproxyHA Local`.
4. Importar la colección y ejecutar `POST Ingest DTE`.
   - Antes de ingresar el DTE, el tenant debe tener `fchResol` y `nroResol` y debe existir un certificado activo cuyo `rutUsuario` corresponda a `{{rutEnvia}}`.
   - El receptor se informa completo en el body: se actualiza por `(tenantId, rutReceptor)` si existe o se crea si es nuevo.
   - Los detalles de `items` no requieren un producto previamente registrado; el servidor genera el identificador interno de cada línea.
   - La colección guardará el `documentId` (o `id`) en la variable `documentId` del environment si la respuesta contiene el campo.
5. Ejecutar `GET Document Status` y `GET Document XML (presigned)` usando la variable `{{documentId}}`.
6. Si cambia el formato de generación o se rota la credencial, ejecutar
   `POST Regenerate Signed XML`. La operación conserva DTE, folio y CAF,
   reemplaza el XML en el mismo `objectKey` y actualiza sus metadatos.

Notas:
- `PUT Partial Update Tenant` modifica solamente los campos enviados. Omitir
  `receptores` conserva la colección actual; enviar `"receptores": []` la vacía.
- `fchResol` usa el formato `YYYY-MM-DD` y `nroResol` acepta valores entre
  `0` y `999999`.
- `rutEmisor` y los demás datos del emisor siempre se leen desde el tenant registrado; `POST /api/v1/dte` no los modifica.
- `POST /api/v1/dte` almacena el `EnvioDTE` y responde metadatos con estado `STORED`.
- La regeneración responde `409` si existe otra operación activa y no debe
  utilizarse para modificar los datos tributarios del DTE persistido.
