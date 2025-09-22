-- IEMS Backend - Task Service Seed Data
-- This script creates initial tasks

-- Note: This script will create sample tasks, but you need to replace the project_id values
-- with actual project IDs from the project service after they are created

-- Insert Initial Tasks (project_id will be set dynamically)
-- These are sample tasks that will be created for demonstration purposes
-- The actual project_id values should be retrieved from the project service

-- Sample task data (project_id will be updated when projects are created)
INSERT INTO tasks (id, project_id, title, description, assigned_to, created_by, status, priority, start_date, due_date, created_at, updated_at, updated_by) VALUES
(gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Setup Development Environment', 'Setup development environment for IEMS system development', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'COMPLETED', 'HIGH', '2024-01-01', '2024-01-15', NOW(), NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Design Database Schema', 'Design and implement database schema for all services', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'IN_PROGRESS', 'HIGH', '2024-01-16', '2024-02-15', NOW(), NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Implement User Authentication', 'Implement JWT-based authentication system', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'TO_DO', 'MEDIUM', '2024-02-16', '2024-03-15', NOW(), NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Create API Documentation', 'Create comprehensive API documentation for all services', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'TO_DO', 'LOW', '2024-03-16', '2024-04-15', NOW(), NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Implement File Upload', 'Implement file upload functionality for document service', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'TO_DO', 'MEDIUM', '2024-04-16', '2024-05-15', NOW(), NOW(), '00000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

