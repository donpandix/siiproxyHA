# 6. Riesgos de implementación y mitigaciones

Errores comunes y cómo evitarlos (normativo):

- Acoplamiento directo al proveedor S3 en `application`: **no debe**. Mitigación: usar `StoragePort` y pruebas con MinIO.
- Asignación de folios sin garantía atómica: **no debe**. Mitigación: implementar `FolioPort` con operación transaccional en BD y lock optimista/seriado.
- Almacenar blobs en BD: **no debe**. Mitigación: almacenar solo `objectKey` y metadatos; usar MinIO para artefactos.

Acoplamientos peligrosos (alerta):

- Lógica de armado de TED o firma dentro de controladores REST. Mitigación: moverla a `application`/`processors` y delegar firma a `SignerPort`.
- Uso de retries sin idempotencia: **no debe**. Mitigación: diseñar consumidores y operaciones idempotentes con `documentId` + `attempt`.

Decisiones críticas que afectan escalabilidad:

- Modelo de folios: si el sistema usa una tabla con lock exclusivo por cada folio en alta concurrencia, puede convertirse en cuello de botella. Recomendación: diseñar el `FolioPort` para reservar bloques (batch allocation) cuando el tráfico lo justifique.
- Transacciones que bloqueen la BD durante storage en S3: **no debe**. Recomendación: esquema eventual consistent: persistir metadata provisional y confirmar tras storage exitoso.

Riesgos operacionales:

- Pérdida de artefactos por naming collision en MinIO. Mitigación: usar `documentId` + `folio` + timestamp en `objectKey`.
- Exposición de claves/CAF en repositorio. Mitigación: usar KMS/LocalStack para desarrollo y variables de entorno en CI/CD.

Checklist de mitigación antes de merge (obligatorio):

- [ ] Revisión: ¿Se respetan los puertos y capas? (domain no depende de infrastructure)
- [ ] Tests: unitarios y de integración añadidos.
- [ ] Seguridad: no hay credenciales en diffs.
- [ ] Escalabilidad: folio allocation y estrategias de retry documentadas.
