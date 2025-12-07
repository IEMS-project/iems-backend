-- Create project_repositories table
CREATE TABLE IF NOT EXISTS project_repositories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    repo_link VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_project_id (project_id),
    CONSTRAINT fk_project_repositories_project 
        FOREIGN KEY (project_id) 
        REFERENCES projects(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
