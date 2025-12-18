-- Migration script for adding soft delete support to folders and files tables
-- This adds deleted_at column to enable trash/recycle bin functionality

-- Add deleted_at column to folders table
ALTER TABLE folders ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL;

-- Add deleted_at column to stored_files table  
ALTER TABLE stored_files ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL;

-- Create indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_folders_deleted_at ON folders(deleted_at);
CREATE INDEX IF NOT EXISTS idx_stored_files_deleted_at ON stored_files(deleted_at);

-- Create composite index for owner + deleted queries
CREATE INDEX IF NOT EXISTS idx_folders_owner_deleted ON folders(owner_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_stored_files_owner_deleted ON stored_files(owner_id, deleted_at);
