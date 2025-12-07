-- Add file_id column to task_attachments table
ALTER TABLE task_attachments ADD COLUMN IF NOT EXISTS file_id VARCHAR(255);

-- Add comment to explain the column
COMMENT ON COLUMN task_attachments.file_id IS 'ID of the file in document service for deletion purposes';
