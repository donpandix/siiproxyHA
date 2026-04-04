# Instrucciones operativas para Copilot

Propósito: manual operativo y normativo que define cómo debe comportarse Copilot/IA al generar o modificar código en este repositorio. Todas las reglas son obligatorias a menos que se indique lo contrario.

1) Fuente de verdad

- `ai-guides/` es la única fuente de verdad para decisiones arquitectónicas, convenciones de paquetes, estándares de código y flujos DTE.
- Antes de generar cualquier código, Copilot debe cargar y consultar: `ai-guides/SKILL.md`, `ai-guides/.instructions.md` y los archivos relevantes dentro de `ai-guides/90-ai-rules/`.
- Si hay conflicto entre documentos, Copilot debe abstenerse de generar código y documentar el conflicto en el PR para revisión humana.

2) Orden de lectura sugerido según tarea

- Tarea arquitectónica: `ai-guides/90-ai-rules/*` → `ai-guides/02-architecture-principles.md` → `ai-guides/03-modular-structure.md` → `ai-guides/90-ai-rules/01-architecture-structure.md`.
- Tarea del dominio DTE: `ai-guides/40-dte/*` → `ai-guides/08-dte-domain-guidelines.md` → `ai-guides/90-ai-rules/02-extension-model.md`.
- Tarea de infraestructura: `ai-guides/07-local-dev-and-infra.md` → `ai-guides/infra/*` → `ai-guides/90-ai-rules/04-integrations-rules.md`.
- Tarea de testing: `ai-guides/05-testing-strategy.md` → `ai-guides/templates/` → `ai-guides/90-ai-rules/05-quality-rules.md`.
- Tarea de refactorización: `ai-guides/90-ai-rules/06-ai-behavior-rules.md` → `ai-guides/90-ai-rules/03-packages-rules.md` → `ai-guides/03-modular-structure.md`.

3) Reglas de generación de código (normativas)

- Copilot debe respetar la separación de capas: `domain` ← `application` ← `infrastructure` ← `interfaces`.
- Copilot no debe introducir lógica de infraestructura (parsing XML, S3 keys, transacciones) dentro del `domain`.
- Copilot no debe crear nuevas estructuras de paquetes o módulos si existe una convención documentada en `ai-guides/03-modular-structure.md`.
- Todo cambio debe ser compatible con Java 21 y versiones de Spring Boot documentadas; no usar APIs no compatibles.
- Copilot debe preferir extensibilidad pragmática: diseñar puertos y adaptadores sencillos antes que abstracciones genéricas complejas.
- Copilot no debe introducir dependencias externas sin agregar en el PR: 1) justificación, 2) evaluación de riesgo y 3) plan de pruebas.

4) Reglas para nuevas features

- Proponer cambios sin romper consistencia: cuando se requiera añadir comportamiento, primero buscar si existe un puerto/servicio reutilizable en `domain`.
- Cuando extender el core (añadir puerto o tipo shared) se requiere: 1) justificar en PR, 2) añadir tests unitarios, 3) documentar la versión del esquema si afecta persistencia.
- Crear componentes específicos por tipo de DTE (p. ej. `Factura33Processor`) cuando la lógica no sea reusable; estos deben vivir en `application.processors` o en módulos `dte-extensions`.
- Documentar decisiones nuevas en el PR: archivos afectados, alternativas consideradas (mínimo 2), y razones para la elección.

5) Reglas para refactor

- Se puede mejorar claridad, nombres y extraer métodos para reducir complejidad ciclomática.
- No se debe cambiar la firma pública de endpoints ni la estructura de la BD sin migración y documentación de compatibilidad.
- Al refactorizar, preservar compatibilidad estructural: paquetes, puertos y contratos públicos deben mantenerse o versionarse explícitamente.
- Refactor que cambia comportamiento debe incluir tests que demuestren equivalencia y casos de borde.

6) Checklist previo a escribir código (debe validar antes de generar archivos nuevos)

- [ ] Revisar `ai-guides/SKILL.md` y `ai-guides/.instructions.md`.
- [ ] Revisar el archivo específico del dominio o capa (ver orden de lectura).
- [ ] Confirmar que no existe ya un puerto/adaptador que cumpla la necesidad.
- [ ] Confirmar esquema OpenAPI si se trata de un endpoint público (`ai-guides/api/openapi.yaml`).
- [ ] Evaluar si la tarea requiere cambios en DB; en ese caso, preparar `schema.sql` y migración plan.
- [ ] No agregar dependencias sin justificación; documentar alternativa nativa.

7) Checklist posterior (debe cumplirse antes de abrir PR)

- [ ] Coherencia: los cambios respetan las reglas en `ai-guides/`.
- [ ] Compatibilidad: nombres de paquetes y clases siguen la convención `cl.cesarg.siiproxyha.*`.
- [ ] Tests: tests unitarios añadidos y al menos un test de integración para endpoints/flows críticos.
- [ ] OpenAPI: `ai-guides/api/openapi.yaml` actualizado si el contrato público cambió.
- [ ] Seguridad: no hay credenciales en diffs; uso de variables de entorno comprobado.
- [ ] Logs/Errores: mensajes estructurados y mapping de errores según `ai-guides/06-logging-error-handling.md`.
- [ ] PR: incluir sección "Decisiones" con alternativas evaluadas, riesgo de la elección y plan de rollback.

8) Reglas adicionales operativas

- Si Copilot encuentra ambigüedad en las reglas, debe abrir un issue con opciones propuestas en lugar de tomar una decisión unilateral.
- Copilot debe incluir en el PR referencias a los archivos de `ai-guides/` que se usaron como base para la implementación.
- Copilot debe respetar los checklists automáticos definidos en `ai-guides/90-ai-rules/06-ai-behavior-rules.md`.

Fin: este documento es operativo y debe revisarse periódicamente. Cualquier cambio normativo se hace mediante PR que actualice `ai-guides/` y que cumpla las mismas reglas aquí descritas.
