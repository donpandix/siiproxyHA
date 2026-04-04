# 1. Alcance inicial del sistema — DTE tipo 33 (Factura Electrónica)

Objetivo: documentar qué incluye y qué queda fuera en la primera etapa (MVP) para centrar el desarrollo en un vertical slice funcional y entregable.

Lo que cubre esta primera etapa (debe):

- Recepción de solicitudes de emisión para DTE tipo 33 via HTTP REST (`POST /api/v1/dte`).
- Validación sintáctica (XML/XSD) y validación semántica básica de negocio (RUT emisor/receptor, montos, obligatoriedad de campos mínimos para tipo 33).
- Asignación segura de folio fiscal (coordinado con un servicio local o tabla de folios) mediante un puerto `FolioPort`.
- Uso de CAF (Certificado de Autorización de Folios) para firmar folios cuando aplique; CAF será un artefacto en MinIO o provisto por external key store según configuración.
- Generación de TED y construcción del XML definitivo conforme al esquema requerido por SII.
- Firma digital del XML (KMS/LocalStack en desarrollo) y almacenamiento del XML firmado en MinIO.
- Persistencia de metadatos en PostgreSQL (`document_metadata`, `processing_history`, `folio_assignments`).
- Encolado de mensajes para procesamiento asíncrono (SQS) para tareas de envío, reintentos y notificaciones.
- Endpoint para consultar estado y recuperar artifactos (`GET /api/v1/dte/{id}/status`, `GET /api/v1/dte/{id}/xml`).

Lo que queda explícitamente fuera (no debe):

- Integración en producción con SII real (solo se documenta y se prepara el adaptador; no se implementa transmisión al SII en esta etapa).
- Soporte de tipos de DTE distintos al 33 (61, 34, 52); diseño preparado para extensión pero sin implementarlos aún.
- Portal UI de administración (solo API y herramientas CLI/scripts para ops).

Qué se considera MVP monetizable (se recomienda priorizar):

- Emisión fiable de Factura Electrónica tipo 33 con firma y almacenamiento verificable.
- API para consulta y descarga de XML firmado.
- Registro y trazabilidad auditables en BD para procesos y reintentos.

Requisitos operativos mínimos:

- Logs estructurados y trazabilidad (requestId/traceId). 
- Pruebas de integración que funcionen localmente con `docker-compose` (Postgres + MinIO + LocalStack).
