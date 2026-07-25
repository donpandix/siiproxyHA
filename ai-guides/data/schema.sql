-- Esquema inicial para SiiproxyHA
-- Tabla: document_metadata
CREATE TABLE IF NOT EXISTS document_metadata (
  id BIGSERIAL PRIMARY KEY,
  document_id VARCHAR(200) NOT NULL UNIQUE,
  folio VARCHAR(100),
  status VARCHAR(50) NOT NULL,
  object_key VARCHAR(512),
  sha256 VARCHAR(64),
  size_bytes BIGINT,
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  last_error VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_document_metadata_document_id ON document_metadata(document_id);

-- Tabla: processing_history (registro de eventos de procesamiento)
CREATE TABLE IF NOT EXISTS processing_history (
  id BIGSERIAL PRIMARY KEY,
  document_id VARCHAR(200) NOT NULL,
  from_state VARCHAR(50),
  to_state VARCHAR(50) NOT NULL,
  actor VARCHAR(100) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_processing_history_document
    FOREIGN KEY(document_id)
      REFERENCES document_metadata(document_id)
      ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_processing_history_document_created
  ON processing_history(document_id, created_at);

-- Notas:
-- - Requiere la extensión pgcrypto o uuid-ossp para generación de UUIDs si no se usa gen_random_uuid().
-- - Ajustar tipos y longitudes según necesidades de producción.
