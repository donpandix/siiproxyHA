# 2. Principios de Arquitectura

1. Separación clara de responsabilidades: `core` (modelo y reglas), `adapters` (DB, storage, messaging), `api` (controladores y DTOs), `config`.
2. Core agnóstico: el dominio debe ser independiente de frameworks (poca o ninguna dependencia de Spring en `core`).
3. Adaptadores intercambiables: persistencia y storage implementan interfaces definidas en `core`.
4. Simplicidad pragmática: evitar patrones complejos a menos que el caso de uso lo justifique.
5. Testabilidad: lógica de negocio probada sin depender de infra; integraciones validadas con `docker-compose`.
6. Observabilidad: logging estructurado, métricas y health checks en endpoints `/actuator/health`.
7. Seguridad por defecto: no almacenar secretos, validar inputs, practicar menor privilegio.

Estrategia de integración:
- Capa de servicio síncrona para flujos inmediatos y colas para trabajo asíncrono (SQS en LocalStack inicialmente).
- Persistencia en Postgres; archivos grandes (XML, PDF) en S3/MinIO con claves referenciadas en la BD.
