-- IEMS Backend - Department Service Seed Data
-- This script creates initial departments

-- Insert Initial Departments
INSERT INTO departments (id, department_name, description, manager_id, created_at, created_by, updated_at, updated_by) VALUES
(gen_random_uuid(), 'Information Technology', 'IT Department responsible for system development and maintenance', '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), 'Human Resources', 'HR Department responsible for employee management and recruitment', '00000000-0000-0000-0000-000000000002', NOW(), '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), 'Finance', 'Finance Department responsible for financial management and accounting', '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), 'Marketing', 'Marketing Department responsible for brand promotion and customer acquisition', '00000000-0000-0000-0000-000000000002', NOW(), '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001'),
(gen_random_uuid(), 'Operations', 'Operations Department responsible for daily business operations', '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001', NOW(), '00000000-0000-0000-0000-000000000001')
ON CONFLICT (department_name) DO NOTHING;

