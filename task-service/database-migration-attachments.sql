-- Create task_attachments table
CREATE TABLE IF NOT EXISTS task_attachments (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_url TEXT NOT NULL,
    file_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL,
    uploaded_by UUID,
    CONSTRAINT fk_task_attachments_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- Create index for faster lookup
CREATE INDEX IF NOT EXISTS idx_task_attachments_task_id ON task_attachments(task_id);
CREATE INDEX IF NOT EXISTS idx_task_attachments_uploaded_by ON task_attachments(uploaded_by);
