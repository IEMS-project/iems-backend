-- IEMS Backend - Project Service Seed Data
-- This script creates initial projects

-- Insert Initial Projects
INSERT INTO projects (id, name, description, start_date, end_date, status, manager_id, created_by, created_at, updated_at) VALUES
(gen_random_uuid(), 'IEMS System Development', 'Main project for developing the Integrated Enterprise Management System', '2024-01-01 00:00:00', '2024-12-31 23:59:59', 'IN_PROGRESS', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()),
(gen_random_uuid(), 'Mobile App Development', 'Development of mobile application for IEMS system', '2024-02-01 00:00:00', '2024-11-30 23:59:59', 'PLANNING', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', NOW(), NOW()),
(gen_random_uuid(), 'System Integration', 'Integration of various modules and third-party services', '2024-03-01 00:00:00', '2024-10-31 23:59:59', 'PLANNING', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW()),
(gen_random_uuid(), 'User Training Program', 'Training program for end users on IEMS system usage', '2024-06-01 00:00:00', '2024-08-31 23:59:59', 'PLANNING', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', NOW(), NOW()),
(gen_random_uuid(), 'System Maintenance', 'Ongoing maintenance and support for IEMS system', '2024-01-01 00:00:00', '2025-12-31 23:59:59', 'IN_PROGRESS', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

