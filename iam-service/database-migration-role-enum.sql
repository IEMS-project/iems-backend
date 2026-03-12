-- Migration: Convert from Role entity to UserRole enum
-- Date: 2026-03-12
-- Available roles: ADMIN, USER

-- Step 1: Add role column to accounts table (if not exists)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS role VARCHAR(20);

-- Step 2: Migrate data from iam_user_roles to accounts.role (if iam_user_roles exists)
-- This assumes 1-to-1 relationship (single role per user)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'iam_user_roles') THEN
        UPDATE accounts a
        SET role = r.code
        FROM iam_user_roles ur
        JOIN iam_roles r ON ur.role_id = r.id
        WHERE a.id = ur.user_id;
    END IF;
END $$;

-- Step 3: Set default role for users without role
UPDATE accounts SET role = 'USER' WHERE role IS NULL;

-- Step 4: Optional - Drop iam_user_roles table (uncomment if ready)
-- DROP TABLE IF EXISTS iam_user_roles;

-- Step 5: Optional - Drop iam_roles table (uncomment if ready)
-- DROP TABLE IF EXISTS iam_roles;

-- Verification queries:
-- SELECT id, username, email, role FROM accounts;
-- SELECT COUNT(*) FROM accounts WHERE role IS NULL;
