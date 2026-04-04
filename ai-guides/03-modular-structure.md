# 3. Estructura modular y convenciones de paquetes

Estructura recomendada (módulos / paquetes):

- `cl.cesarg.siiproxyha.core` — entidades, servicios de dominio puro, puertos (interfaces).
- `cl.cesarg.siiproxyha.adapters.persistence` — implementaciones de repositorios (JPA/Hibernate, mappers).
- `cl.cesarg.siiproxyha.adapters.storage` — adaptadores S3/MinIO.
- `cl.cesarg.siiproxyha.adapters.messaging` — integración con SQS/LocalStack.
- `cl.cesarg.siiproxyha.api` — DTOs, controladores REST, mapeadores entrada/salida.
- `cl.cesarg.siiproxyha.config` — configuración de Spring, beans, perfiles.

Reglas de diseño:

- Interfaces de puertos deben residir en `core` y ser lo más pequeñas posibles.
- Adaptadores implementan puertos y se registran como beans sólo en los módulos Spring.
- DTOs separados de entidades; mapeo explícito (MapStruct o ensambladores manuales según equipo).
- Evitar anotaciones de persistencia (`@Entity`) dentro del `core`.

Convenciones de nombres:

- Repositorios: `XxxRepository` (adapter) / `XxxPort` (puerto en core).
- Servicios de negocio: `XxxService` en `core` y `XxxServiceImpl` en adapter si aplica.
