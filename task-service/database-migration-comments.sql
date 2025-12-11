-- Migration: Update task_comment table for rich text support
-- Date: 2024-12-11

-- Change content column from VARCHAR(2000) to TEXT for rich text HTML support
ALTER TABLE task_comment MODIFY COLUMN content TEXT NOT NULL;

-- Add updated_at column for tracking comment edits
ALTER TABLE task_comment ADD COLUMN updated_at TIMESTAMP NULL;

-- Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_task_comment_task_id ON task_comment(task_id);
CREATE INDEX IF NOT EXISTS idx_task_comment_created_at ON task_comment(created_at);
