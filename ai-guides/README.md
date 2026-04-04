# ai-guides

Carpeta de verdad única para asistentes de IA y desarrolladores. Contiene las decisiones arquitectónicas, convenciones, plantillas y artefactos que Copilot deberá consultar antes de generar código nuevo.

Estructura breve:

- `SKILL.md` — instrucciones formales para agentes/IA.
- `.instructions.md` — reglas aplicables siempre.
- `01-vision.md` — visión general del proyecto.
- `02-architecture-principles.md` — principios arquitectónicos.
- `03-modular-structure.md` — estructura modular y convenciones de paquetes.
- `04-java-spring-standards.md` — estándares Java 21 / Spring Boot.
- `05-testing-strategy.md` — estrategia de pruebas.
- `06-logging-error-handling.md` — logging y manejo de errores.
- `07-local-dev-and-infra.md` — entorno local e integración con infra.
- `08-dte-domain-guidelines.md` — lineamientos del dominio DTE.
- `09-extensibility-guidelines.md` — reglas para extensión a otros DTE.
- `10-ai-copilot-guidelines.md` — instrucciones específicas para IA y Copilot.
- `templates/` — plantillas de endpoints, issues y PRs.
- `infra/` — documentación de `docker-compose` y ejemplos de desarrollo.

Uso:

1. Antes de generar código nuevo, Copilot debe cargar `SKILL.md` y `.instructions.md`.
2. Consultar `03-modular-structure.md` y `04-java-spring-standards.md` para el diseño y estilo.
3. Actualizar `openapi.yaml` en `api/` cuando se agreguen o modifiquen endpoints.

Mantén estos archivos actualizados — son la fuente de verdad para decisiones automáticas.
