# 10. Instrucciones específicas para asistentes de IA y Copilot

Propósito: definir cómo Copilot/IA debe comportarse al generar o modificar código en este repositorio.

Reglas de comportamiento (alto nivel):

- Antes de cualquier generación, cargar `ai-guides/SKILL.md` y `ai-guides/.instructions.md`.
- Priorizar `core` inmutable: cambios en dominio requieren justificación y tests.
- No introducir dependencias nuevas sin proponer y justificar en el PR.
- Mantener la convención de paquetes y nombres descrita en `03-modular-structure.md`.

Plantilla mínima para PRs generados automáticamente:

```
Resumen: Qué se generó / cambió
Motivación: Por qué (referencia a issue o requerimiento)
Decisiones: Opciones evaluadas y razón de la elección
Cambios notables: archivos principales y efectos secundarios
Tests incluidos: lista de tests añadidos
Notas para revisión manual: puntos a revisar por humanos
```

Chequeos automáticos que la IA debe ejecutar antes de proponer un cambio:

- Actualizar `ai-guides/api/openapi.yaml` si se cambian contratos.
- Confirmar que no se incluyan credenciales en el diff.
- Incluir tests unitarios y al menos 1 test de integración para nuevos endpoints.

Frases de activación recomendadas para prompts del equipo:

- "Genera un endpoint REST para..."
- "Implementa el adapter de storage que persista en S3/MinIO..."
- "Crea tests de integración que usen docker-compose para Postgres y MinIO"
