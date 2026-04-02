-- Safe migration for project_document.allow_embedded
-- Run this once on document-db before starting document-service.

ALTER TABLE project_document
    ADD COLUMN IF NOT EXISTS allow_embedded BOOLEAN;

UPDATE project_document
SET allow_embedded = FALSE
WHERE allow_embedded IS NULL;

ALTER TABLE project_document
    ALTER COLUMN allow_embedded SET DEFAULT FALSE;

ALTER TABLE project_document
    ALTER COLUMN allow_embedded SET NOT NULL;
