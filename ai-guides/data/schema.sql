-- Esquema inicial para SiiproxyHA
-- Tabla: document_metadata
CREATE TABLE IF NOT EXISTS document_metadata (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id VARCHAR(200) NOT NULL UNIQUE,
  folio VARCHAR(100),
  status VARCHAR(50) NOT NULL,
  object_key VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_document_metadata_document_id ON document_metadata(document_id);

-- Tabla: processing_history (registro de eventos de procesamiento)
CREATE TABLE IF NOT EXISTS processing_history (
  id BIGSERIAL PRIMARY KEY,
  document_id VARCHAR(200) NOT NULL,
  status VARCHAR(50) NOT NULL,
  message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_document
    FOREIGN KEY(document_id)
      REFERENCES document_metadata(document_id)
      ON DELETE CASCADE
);

-- Notas:
-- - Requiere la extensión pgcrypto o uuid-ossp para generación de UUIDs si no se usa gen_random_uuid().
-- - Ajustar tipos y longitudes según necesidades de producción.
