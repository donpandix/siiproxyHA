---
name: ai-guides
description: "Guía base del proyecto: arquitectura, convenciones y reglas que la IA debe consultar. Use when: generar controladores, repositorios, servicios, mapeos entidad-DTO, pruebas, infraestructura local. Keywords: Postgres, MinIO, S3, Spring Boot, Java 21, DTE, Factura 33, REST, OpenAPI"
---

# SKILL: ai-guides

Este archivo contiene las reglas que deben cargarse antes de que un agente/IA genere código para este repositorio. Incluye frases clave para detección automática y líneas normativas.

Uso requerido por agentes:

- Siempre cargar `SKILL.md` al iniciar una tarea de generación de código.
- Consultar `.instructions.md` para reglas inmutables (naming, seguridad, testing).
- Validar que cualquier cambio a modelos o endpoints actualice simultáneamente `api/openapi.yaml`.

Palabras clave de activación (no exhaustivo): `DTE`, `Factura 33`, `Postgres`, `MinIO`, `S3`, `LocalStack`, `Spring Boot`, `Java 21`, `REST`, `OpenAPI`, `Repository`, `Service`, `Controller`.

Guía corta para decisiones que la IA puede aplicar:

- Preferir composición sobre herencia para código de dominio.
- Mantener un `core` independiente de adaptadores (persistence, storage, messaging).
- Evitar cambios globales sin tests de integración que usen `docker-compose`.
