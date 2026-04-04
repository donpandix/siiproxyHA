# Infra: docker-compose para desarrollo

El repositorio ya contiene un `docker-compose.yml` con servicios útiles: Postgres 17, MinIO y LocalStack.

Recomendaciones:

- Copiar o incluir `docker-compose.yml` como `docker-compose.dev.yml` con los servicios mínimos para pruebas locales.
- `docker-compose.dev.yml` debería exponer puertos locales (`5432`, `9000`, `4566`).
- Mantener volúmenes nombrados para persistencia local.

Variables de entorno sugeridas (`env.example`):

```
DB_URL=jdbc:postgresql://localhost:5432/dte
DB_USER=dte_user
DB_PASS=dte_pass
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minio
S3_SECRET_KEY=minio123
AWS_REGION=us-east-1
```

Nota: usar MinIO para desarrollo y pruebas locales; el adaptador S3 debe admitir endpoints custom y path-style.
