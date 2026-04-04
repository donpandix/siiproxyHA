# 1. Visión general del proyecto

Objetivo: ofrecer un microservicio backend responsable de emitir y procesar DTE (especialmente Factura Electrónica, tipo 33) para integrarse con el SII y procesos internos, con persistencia en PostgreSQL y almacenamiento de archivos/XML en S3/MinIO.

Alcance inicial:
- Soporte del flujo completo de Factura Electrónica (tipo 33): recibido, validación, firma/ensamblado, almacenamiento de XML, persistencia de metadatos, colas para procesamiento asíncrono y entrega a servicios externos.

Principios de producto:
- Robustez funcional sobre características no esenciales.
- Extensibilidad por tipo de DTE sin romper el `core`.
- Observabilidad y pruebas automatizadas desde el inicio.

Audiencia: desarrolladores backend, arquitectos y asistentes de IA que generen código alineado con estas reglas.
