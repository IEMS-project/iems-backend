-- Safe migration for optimistic locking on global document tables.
-- Run this once on document-db before starting document-service with @Version fields.
--
-- Entity table names in the current code:
--   StoredFile -> file
--   Folder     -> folder
--   Share      -> share
--   Favorite   -> favorite

ALTER TABLE "file"
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE "file"
SET version = 0
WHERE version IS NULL;

ALTER TABLE "file"
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;


ALTER TABLE folder
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE folder
SET version = 0
WHERE version IS NULL;

ALTER TABLE folder
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;


ALTER TABLE "share"
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE "share"
SET version = 0
WHERE version IS NULL;

ALTER TABLE "share"
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;


ALTER TABLE favorite
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE favorite
SET version = 0
WHERE version IS NULL;

ALTER TABLE favorite
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;
