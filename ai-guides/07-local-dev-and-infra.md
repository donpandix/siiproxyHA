# 7. Entorno de desarrollo local e integración con infraestructura

Servicios disponibles (por `docker-compose`):

- PostgreSQL 17: `dte` / `dte_user` / `dte_pass` en puerto `5432`.
- MinIO: credenciales `minio`/`minio123`, API `9000`, consola `9001`.
- LocalStack: SQS, SSM, KMS en `4566` (región `us-east-1`).

Recomendaciones de uso local:

- Mantener `env.example` en el repo con variables necesarias: `DB_URL`, `DB_USER`, `DB_PASS`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `AWS_REGION`.
- Para pruebas locales usar MinIO y LocalStack; los adaptadores deben soportar endpoint custom.
- Incluir un `docker-compose.dev.yml` con servicios mínimos para tests de integración.

Conexión a servicios en code:

- Postgres URL ejemplo: `jdbc:postgresql://localhost:5432/dte`.
- MinIO endpoint: `http://localhost:9000` con signature compatible S3.

Notas de seguridad local:

- No hardcodear credenciales; usar `.env` local excluido por `.gitignore`.
