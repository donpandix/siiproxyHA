# 3. Reglas de paquetes y dependencias

Convenciones de nombres (obligatorio):

- Paquete raíz: `cl.cesarg.siiproxyha`.
- `cl.cesarg.siiproxyha.domain` — modelos y puertos (interfaces).
- `cl.cesarg.siiproxyha.application` — casos de uso y servicios de aplicación.
- `cl.cesarg.siiproxyha.infrastructure.persistence` — repositorios JPA/JDBC y mappers.
- `cl.cesarg.siiproxyha.infrastructure.storage` — adaptadores S3/MinIO.
- `cl.cesarg.siiproxyha.interfaces.rest` — controladores, DTOs y mapeadores.

Dependencias permitidas y prohibidas:

- `domain` no puede depender de ningún paquete `infrastructure` ni `interfaces`.
- `application` puede depender de `domain` y librerías utilitarias, pero no de `interfaces`.
- `infrastructure` puede depender de `application` y `domain` y debe agrupar toda la lógica concreta de integración.
- Está prohibido usar clases `@Entity` del paquete `infrastructure.persistence` como parámetros en controladores o DTOs.

Tipos de clases por módulo (solo pueden):

- `domain`: entidades de negocio, value objects, excepciones de dominio, interfaces de puertos.
- `application`: servicios orquestadores, validaciones de casos de uso, DTOs internos de aplicación.
- `infrastructure.persistence`: entidades JPA, repositorios, migraciones SQL, mappers to/from domain.
- `infrastructure.storage`: clientes S3/MinIO, utilidades de firma, manejo de keys.
- `interfaces.rest`: controllers REST, request/response DTOs, mappers a objetos de `application`.

Reglas de import y visibilidad:

- Solo `application` y `interfaces` pueden mapear DTOs hacia/desde `domain` usando mappers explícitos.
- No usar imports circulares; cualquier dependencia cruzada debe resolverse por inyección de puertos.
