# siiproxyHA

Aplicación backend para gestionar tenants, receptores, certificados, folios CAF y documentos tributarios electrónicos (DTE). Está construida con Java 21 y Spring Boot, y utiliza PostgreSQL, MinIO y LocalStack como infraestructura local.

## Requisitos

Antes de comenzar, instala:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) y asegúrate de que esté iniciado.
- Java 21. Puedes comprobar la versión con:

  ```bash
  java -version
  ```

No es necesario instalar Maven: el repositorio incluye Maven Wrapper (`mvnw`).

## Instalación local

1. Clona el repositorio y entra en su directorio:

   ```bash
   git clone <URL_DEL_REPOSITORIO>
   cd siiproxyHA
   ```

2. Crea el archivo de configuración local a partir del ejemplo:

   ```bash
   cp .env.example .env
   ```

   Los valores de `.env.example` están preparados para desarrollo local. Si modificas usuarios, contraseñas o puertos, mantén los mismos valores en la configuración de Docker y de Spring Boot.

3. Levanta PostgreSQL, MinIO y LocalStack:

   ```bash
   docker compose up -d
   ```

4. Comprueba que los contenedores estén ejecutándose:

   ```bash
   docker compose ps
   ```

5. La aplicación verifica y crea automáticamente los buckets configurados al iniciar:

   - `dte-bucket`
   - `certificates-bucket`

   Si cambias `MINIO_BUCKET` o `MINIO_CERTIFICATES_BUCKET` en `.env`, se crearán los nombres configurados. Las credenciales de MinIO deben tener permiso para consultar y crear buckets.

6. Ejecuta la aplicación:

   ```bash
   ./mvnw spring-boot:run
   ```

   En Windows, utiliza:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

Durante el arranque, Flyway aplicará automáticamente las migraciones de base de datos. La API quedará disponible en [http://localhost:8080](http://localhost:8080).

## Verificar la instalación

Con la aplicación en ejecución, consulta el endpoint de salud:

```bash
curl http://localhost:8080/api/v1/health
```

También puedes consultar el endpoint estándar de Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health
```

Una respuesta con estado `UP` confirma que la aplicación está funcionando.

## Probar la API con Postman

El repositorio incluye una colección y un entorno local:

- `documents/postman/siiproxyha-collection.json`
- `documents/postman/siiproxyha-environment.json`

Importa ambos archivos en Postman y selecciona el entorno `siiproxyHA Local`. También hay archivos XML de ejemplo en `documents/samples/`.

## Servicios locales

| Servicio | Dirección | Credenciales predeterminadas |
| --- | --- | --- |
| API | `http://localhost:8080` | No aplica |
| PostgreSQL | `localhost:5432` | `dte_user` / `dte_pass` |
| MinIO API | `http://localhost:9000` | `minio` / `minio123` |
| MinIO Console | `http://localhost:9001` | `minio` / `minio123` |
| LocalStack | `http://localhost:4566` | No aplica |

Los valores efectivos se encuentran en tu archivo `.env`.

## Comandos útiles

Ejecutar las pruebas:

```bash
./mvnw test
```

Detener la infraestructura sin borrar los datos:

```bash
docker compose stop
```

Detener y eliminar los contenedores, conservando los volúmenes:

```bash
docker compose down
```

Eliminar también los datos locales de PostgreSQL, MinIO y LocalStack:

```bash
docker compose down -v
```

> Este último comando elimina de forma permanente los datos almacenados en los volúmenes locales.

## Solución de problemas

- **El puerto ya está en uso:** detén el servicio que utiliza `5432`, `9000`, `9001`, `4566` o `8080`, o cambia el mapeo correspondiente.
- **La aplicación no conecta a PostgreSQL o MinIO:** confirma que Docker Desktop esté iniciado, ejecuta `docker compose ps` y revisa que `.env` exista en la raíz del proyecto.
- **La aplicación no puede inicializar un bucket de MinIO:** revisa que los nombres configurados sean válidos y que las credenciales tengan permisos para consultar y crear buckets. Como alternativa operativa, créalos manualmente desde [http://localhost:9001](http://localhost:9001).
- **Java usa una versión incorrecta:** configura `JAVA_HOME` para que apunte a una instalación de Java 21.
