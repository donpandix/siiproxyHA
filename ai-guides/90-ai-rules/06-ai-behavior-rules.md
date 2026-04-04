# 6. Reglas para IA (Comportamiento obligatorio de Copilot)

Objetivo: definir cómo debe comportarse Copilot/IA al generar o modificar código en este repositorio. Estas reglas son obligatorias y deben aplicarse antes de crear PRs.

Orden de consulta previo a generación (Copilot debe):

1. Cargar `ai-guides/SKILL.md`.
2. Cargar `ai-guides/.instructions.md`.
3. Cargar `ai-guides/90-ai-rules/*` (todos los archivos de esta carpeta).
4. Consultar `ai-guides/03-modular-structure.md` y `ai-guides/04-java-spring-standards.md`.

Reglas de conducta (debe / no debe):

- Copilot debe priorizar consistencia con la arquitectura y convenciones descritas por sobre la velocidad de entrega.
- Copilot no debe introducir nuevas dependencias de terceros sin documentar la justificación en el PR y añadir una evaluación de riesgo.
- Copilot debe generar tests unitarios y al menos un test de integración para cada endpoint nuevo.
- Copilot debe incluir en el PR un bloque "Decisiones" que explique las opciones consideradas y la razón de la elección.
- Copilot no debe asumir convenciones no documentadas; cuando exista ambigüedad debe solicitar aclaración o proponer opciones enumeradas en el PR.

Reglas para manipulación de secretos y configuraciones:

- Copilot no debe generar código que lea credenciales desde archivos no listados en `.gitignore`.
- Copilot debe usar variables de entorno y placeholders en `application.yaml`.

Reglas para cambios en contratos públicos (OpenAPI):

- Si Copilot modifica un endpoint público, debe actualizar `ai-guides/api/openapi.yaml` y añadir tests de contract.

Checklist automatizable que Copilot debe verificar antes de abrir PR:

- [ ] `ai-guides/SKILL.md` y `.instructions.md` fueron consultados.
- [ ] No hay credenciales en diffs.
- [ ] Tests unitarios añadidos.
- [ ] Test de integración añadido o actualizado.
- [ ] `openapi.yaml` actualizado si aplica.
- [ ] Descripción del PR incluye decisiones arquitectónicas.

Comportamiento al generar código de extensión (nuevo tipo DTE):

- Copilot debe generar un `Processor` específico en `application.processors` y no modificar código del `core` salvo para añadir puertos compatibles y con justificación.
