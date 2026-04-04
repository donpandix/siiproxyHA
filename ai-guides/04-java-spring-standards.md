# 4. Estándares de desarrollo en Java 21 y Spring Boot

Configuración general:

- Target: Java 21.
- Spring Boot 3.x (compatibilidad con Jakarta namespaces).
- Gestionar dependencias en `pom.xml`; evitar dependencias innecesarias en `core`.

Estilo de código y convenciones:

- Usa `var` con moderación; preferir tipos explícitos en APIs públicas.
- Nombres claros en español/inglés según dominio, preferir inglés para paquetes y código técnico.
- Documentar métodos públicos con Javadoc breve (una línea de propósito + parámetros importantes).

Frameworks y librerías recomendadas:

- MapStruct para mapeos DTO-Entidad (opcional).
- Spring Data JPA para repositorios si se necesitan capacidades ORM.
- Spring Web + Spring MVC para REST.
- Spring Boot Actuator para health y métricas.

Configuración y secretos:

- Variables de entorno para credenciales: `DB_URL`, `DB_USER`, `DB_PASS`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`.
- No comprometer `application.yaml` con secretos reales; usar placeholders.

Build y CI:

- `mvnw` wrapper obligatorio.
- Reglas de formato (e.g., Spotless / google-java-format) en CI.
# 4. Estándares de desarrollo en Java 21 y Spring Boot

Entorno objetivo:

- Java 21 LTS
- Spring Boot (versión estable que soporte Java 21)

Convenciones y prácticas:

- Código: seguir Google Java Style o la guía de estilo acordada por el equipo.
- Finalidad de paquetes: reflejar capas, no características transversales.
- Dependencias: preferir bibliotecas maduras (Spring Data, MapStruct, Lombok opcional).
- Null-safety: preferir tipos `Optional` y validaciones con `jakarta.validation`.
- Inmutabilidad: usar objetos inmutables en `core` cuando tenga sentido.

Configuración y perfiles:

- `application.yaml` con perfiles `local`, `dev`, `prod`.
- Valores sensibles vía variables de entorno; usar `spring.config.import=optional:configserver:` si aplica.

Build y calidad:

- Maven wrapper (`mvnw`) como ya presente en el repo.
- Ejecutar `mvn -DskipTests=false verify` en CI.
- Integrar checkstyle/spotbugs si el equipo lo desea.

Dependencias recomendadas:

- Spring Boot Starter Web
- Spring Boot Starter Data JPA (o JDBC) según decisión
- PostgreSQL driver
- AWS SDK v2 o MinIO Java client para storage
- Spring Boot Actuator
