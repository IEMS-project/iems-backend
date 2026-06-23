-- Create issue status history table for burndown tracking
CREATE TABLE IF NOT EXISTS issue_status_histories (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    issue_id UUID NOT NULL,
    sprint_id UUID,
    from_status_id UUID,
    to_status_id UUID NOT NULL,
    story_points INTEGER,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_issue_status_histories_sprint_id_changed_at
    ON issue_status_histories (sprint_id, changed_at);

CREATE INDEX IF NOT EXISTS idx_issue_status_histories_issue_id_changed_at
    ON issue_status_histories (issue_id, changed_at);
