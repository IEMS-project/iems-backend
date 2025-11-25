-- Add status column to project_members table
-- Run this SQL script on your database

ALTER TABLE project_members 
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Add check constraint to ensure only valid status values
ALTER TABLE project_members
ADD CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- Update existing records to have ACTIVE status
UPDATE project_members SET status = 'ACTIVE' WHERE status IS NULL;
