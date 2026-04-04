# 1. Estructura arquitectónica (reglas estrictas)

Objetivo: garantizar separación clara entre dominio, aplicación, infraestructura e interfaces. Estas reglas son normativas y deben cumplirse para cualquier generación de código.

Reglas generales:

- La aplicación debe dividirse en cuatro capas lógicas: `domain` (core), `application` (casos de uso/servicios de aplicación), `infrastructure` (adaptadores a BD, storage, mensajería) y `interfaces` (REST controllers, DTOs).
- El `domain` debe ser independiente de Spring, frameworks y clientes externos; no debe contener anotaciones de infraestructura (`@Entity`, `@Repository`, `@Autowired`).
- El `application` puede depender del `domain` y definir puertos (interfaces) que los adaptadores implementan.
- El `infrastructure` solo puede depender de `application` y `domain` pero nunca al revés.
- La `interfaces` (API) puede depender de `application` y `domain` para construir DTOs y llamar casos de uso.

Reglas prohibidas (no debe):

- No mezclar lógica de negocio con parsing/serialización XML: el parsing XML debe encapsularse en un adaptador en `infrastructure` y transformar a objetos de `domain`.
- No persistir entidades de `domain` directamente: las entidades de persistencia deben vivir en `infrastructure` y mapearse explícitamente.
- No llamar clientes externos (HTTP/S3/SQS) desde `domain`; siempre mediante interfaces/puertos definidos en `application`.

Desacoplamiento del flujo DTE:

- El flujo DTE (ingesta → validación → persistencia → encolado → entrega) debe implementarse como una orquestación en `application` usando solo puertos del `domain`.
- Las transformaciones específicas del formato (XML, firma, CAF) deben residir en adaptadores dentro de `infrastructure` y proveer servicios al `application` mediante interfaces.

Documentación requerida por cada cambio que afecte la arquitectura:

- Todo PR que modifique capas o puertos debe incluir en la descripción: qué capa se toca, por qué es necesario, y pruebas de integración que validen el aislamiento.
