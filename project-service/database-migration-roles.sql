-- Migration: Change project_allowed_roles from UUID roleId to free-text roleName
-- Also update project_members.role_id to reference project_allowed_roles.id instead of old roleId

-- Step 1: Update project_members.role_id to reference project_allowed_roles.id (PK) instead of old role_id
UPDATE project_members pm
SET role_id = par.id
FROM project_allowed_roles par
WHERE pm.role_id = par.role_id
  AND par.project_id = pm.project_id;

-- Step 2: Drop the old unique constraint on (project_id, role_id)
ALTER TABLE project_allowed_roles DROP CONSTRAINT IF EXISTS project_allowed_roles_project_id_role_id_key;
ALTER TABLE project_allowed_roles DROP CONSTRAINT IF EXISTS ukproject_allowed_roles_project_id_role_id;

-- Step 3: Drop the role_id column
ALTER TABLE project_allowed_roles DROP COLUMN IF EXISTS role_id;

-- Step 4: Add new unique constraint on (project_id, role_name) with case-insensitive check
ALTER TABLE project_allowed_roles ADD CONSTRAINT uk_project_allowed_roles_project_role_name UNIQUE (project_id, role_name);
