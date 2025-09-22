-- IEMS Backend - IAM Service Seed Data
-- This script creates initial permissions, roles, and admin accounts

-- Insert Permissions
INSERT INTO iam_permissions (id, code, name, description, active, created_at) VALUES
-- User Management Permissions
(gen_random_uuid(), 'USER_CREATE', 'Create User', 'Permission to create new users', true, NOW()),
(gen_random_uuid(), 'USER_READ', 'Read User', 'Permission to view user information', true, NOW()),
(gen_random_uuid(), 'USER_UPDATE', 'Update User', 'Permission to update user information', true, NOW()),
(gen_random_uuid(), 'USER_DELETE', 'Delete User', 'Permission to delete users', true, NOW()),

-- Project Management Permissions
(gen_random_uuid(), 'PROJECT_CREATE', 'Create Project', 'Permission to create new projects', true, NOW()),
(gen_random_uuid(), 'PROJECT_READ', 'Read Project', 'Permission to view project information', true, NOW()),
(gen_random_uuid(), 'PROJECT_UPDATE', 'Update Project', 'Permission to update project information', true, NOW()),
(gen_random_uuid(), 'PROJECT_DELETE', 'Delete Project', 'Permission to delete projects', true, NOW()),
(gen_random_uuid(), 'PROJECT_MANAGE', 'Manage Project', 'Permission to manage project members and settings', true, NOW()),

-- Task Management Permissions
(gen_random_uuid(), 'TASK_CREATE', 'Create Task', 'Permission to create new tasks', true, NOW()),
(gen_random_uuid(), 'TASK_READ', 'Read Task', 'Permission to view task information', true, NOW()),
(gen_random_uuid(), 'TASK_UPDATE', 'Update Task', 'Permission to update task information', true, NOW()),
(gen_random_uuid(), 'TASK_DELETE', 'Delete Task', 'Permission to delete tasks', true, NOW()),
(gen_random_uuid(), 'TASK_ASSIGN', 'Assign Task', 'Permission to assign tasks to users', true, NOW()),

-- Department Management Permissions
(gen_random_uuid(), 'DEPT_CREATE', 'Create Department', 'Permission to create new departments', true, NOW()),
(gen_random_uuid(), 'DEPT_READ', 'Read Department', 'Permission to view department information', true, NOW()),
(gen_random_uuid(), 'DEPT_UPDATE', 'Update Department', 'Permission to update department information', true, NOW()),
(gen_random_uuid(), 'DEPT_DELETE', 'Delete Department', 'Permission to delete departments', true, NOW()),
(gen_random_uuid(), 'DEPT_MANAGE', 'Manage Department', 'Permission to manage department members', true, NOW()),

-- Document Management Permissions
(gen_random_uuid(), 'DOC_CREATE', 'Create Document', 'Permission to create new documents', true, NOW()),
(gen_random_uuid(), 'DOC_READ', 'Read Document', 'Permission to view documents', true, NOW()),
(gen_random_uuid(), 'DOC_UPDATE', 'Update Document', 'Permission to update documents', true, NOW()),
(gen_random_uuid(), 'DOC_DELETE', 'Delete Document', 'Permission to delete documents', true, NOW()),
(gen_random_uuid(), 'DOC_SHARE', 'Share Document', 'Permission to share documents with others', true, NOW()),

-- System Administration Permissions
(gen_random_uuid(), 'ADMIN_1', 'System Administrator', 'Full system administration access', true, NOW()),
(gen_random_uuid(), 'ADMIN_2', 'User Administrator', 'User management administration access', true, NOW()),
(gen_random_uuid(), 'ADMIN_3', 'Project Administrator', 'Project management administration access', true, NOW()),

-- Reporting Permissions
(gen_random_uuid(), 'REPORT_READ', 'Read Reports', 'Permission to view reports', true, NOW()),
(gen_random_uuid(), 'REPORT_CREATE', 'Create Reports', 'Permission to create reports', true, NOW()),
(gen_random_uuid(), 'REPORT_EXPORT', 'Export Reports', 'Permission to export reports', true, NOW())
ON CONFLICT (code) DO NOTHING;

-- Insert Roles
INSERT INTO iam_roles (id, code, name, description, active, created_at) VALUES
(gen_random_uuid(), 'SUPER_ADMIN', 'Super Administrator', 'Full system access with all permissions', true, NOW()),
(gen_random_uuid(), 'ADMIN', 'Administrator', 'System administrator with most permissions', true, NOW()),
(gen_random_uuid(), 'PROJECT_MANAGER', 'Project Manager', 'Project management role with project and task permissions', true, NOW()),
(gen_random_uuid(), 'TEAM_LEAD', 'Team Lead', 'Team leadership role with limited management permissions', true, NOW()),
(gen_random_uuid(), 'DEVELOPER', 'Developer', 'Developer role with basic project and task permissions', true, NOW()),
(gen_random_uuid(), 'USER', 'User', 'Basic user role with read-only permissions', true, NOW())
ON CONFLICT (code) DO NOTHING;

-- Insert Admin Accounts
-- Password: admin123 (BCrypt encoded)
INSERT INTO accounts (id, user_id, username, email, password_hash, enabled, created_at) VALUES
('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'admin', 'admin@iems.com', '$2a$10$N2gBIfsDd2oKkyVCAL5M/.FJXbowhlzL33yjU8ZtiifBU.2mRwSgW', true, NOW()),
('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'admin2', 'admin2@iems.com', '$2a$10$N2gBIfsDd2oKkyVCAL5M/.FJXbowhlzL33yjU8ZtiifBU.2mRwSgW', true, NOW())
ON CONFLICT (username) DO NOTHING;

-- Assign Roles to Admin Accounts
INSERT INTO iam_user_roles (id, user_id, role_id, active, created_at) 
SELECT 
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001',
    r.id,
    true,
    NOW()
FROM iam_roles r 
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO iam_user_roles (id, user_id, role_id, active, created_at) 
SELECT 
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000002',
    r.id,
    true,
    NOW()
FROM iam_roles r 
WHERE r.code = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign Permissions to Roles
-- SUPER_ADMIN gets all permissions
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam_roles r, iam_permissions p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- ADMIN gets all permissions except ADMIN_1
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam_roles r, iam_permissions p
WHERE r.code = 'ADMIN' AND p.code != 'ADMIN_1'
ON CONFLICT DO NOTHING;

-- PROJECT_MANAGER permissions
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam_roles r, iam_permissions p
WHERE r.code = 'PROJECT_MANAGER' 
AND p.code IN (
    'PROJECT_CREATE', 'PROJECT_READ', 'PROJECT_UPDATE', 'PROJECT_MANAGE',
    'TASK_CREATE', 'TASK_READ', 'TASK_UPDATE', 'TASK_ASSIGN',
    'USER_READ', 'DEPT_READ', 'DOC_CREATE', 'DOC_READ', 'DOC_UPDATE', 'DOC_SHARE',
    'REPORT_READ', 'REPORT_CREATE', 'REPORT_EXPORT'
)
ON CONFLICT DO NOTHING;

-- TEAM_LEAD permissions
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam_roles r, iam_permissions p
WHERE r.code = 'TEAM_LEAD' 
AND p.code IN (
    'PROJECT_READ', 'TASK_CREATE', 'TASK_READ', 'TASK_UPDATE', 'TASK_ASSIGN',
    'USER_READ', 'DEPT_READ', 'DOC_CREATE', 'DOC_READ', 'DOC_UPDATE', 'DOC_SHARE',
    'REPORT_READ', 'REPORT_CREATE'
)
ON CONFLICT DO NOTHING;

-- DEVELOPER permissions
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam_roles r, iam_permissions p
WHERE r.code = 'DEVELOPER' 
AND p.code IN (
    'PROJECT_READ', 'TASK_READ', 'TASK_UPDATE',
    'USER_READ', 'DEPT_READ', 'DOC_READ', 'DOC_UPDATE',
    'REPORT_READ'
)
ON CONFLICT DO NOTHING;

-- USER permissions
INSERT INTO iam_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM iam_roles r, iam_permissions p
WHERE r.code = 'USER' 
AND p.code IN (
    'PROJECT_READ', 'TASK_READ',
    'USER_READ', 'DEPT_READ', 'DOC_READ',
    'REPORT_READ'
)
ON CONFLICT DO NOTHING;
