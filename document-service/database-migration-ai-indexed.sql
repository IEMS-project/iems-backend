-- Safe migration for project_document.ai_indexed
-- Run this once on document-db before starting document-service.

ALTER TABLE project_document
    ADD COLUMN IF NOT EXISTS ai_indexed BOOLEAN;

UPDATE project_document
SET ai_indexed = FALSE
WHERE ai_indexed IS NULL;

ALTER TABLE project_document
    ALTER COLUMN ai_indexed SET DEFAULT FALSE;

ALTER TABLE project_document
    ALTER COLUMN ai_indexed SET NOT NULL;
