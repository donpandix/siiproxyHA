# 9. Reglas para futuras extensiones a otros tipos de DTE

Estrategia general:

- Tratar cada tipo de DTE como un `plugin` lógico que implementa las interfaces del `core`.
- Evitar lógica condicional compleja basada en `tipoDTE` dentro de servicios centrales; delegar a componentes específicos por tipo.

Patrón recomendado:

- Registrar un `DocumentoProcessor` por tipo de DTE (ej: `Factura33Processor`) que implemente las mismas operaciones: `validate`, `store`, `enqueue`, `finalize`.

Versionado y compatibilidad:

- Mantener columnas de `schema_version` y `business_version` en tablas críticas cuando las migraciones lo requieran.

Reglas de aceptación para agregar nuevo tipo DTE:

1. Añadir un procesador específico sin tocar código del `core`.
2. Agregar tests unitarios y de integración para el flujo completo del nuevo tipo.
3. Actualizar `openapi.yaml` si el contrato público cambia.
