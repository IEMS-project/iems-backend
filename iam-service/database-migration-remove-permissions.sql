-- ============================================================
-- Migration: Remove permission system, add subscription to accounts
-- Date: 2026-03-12
-- ============================================================

-- 1. Drop permission-related tables
DROP TABLE IF EXISTS iam_user_permissions;
DROP TABLE IF EXISTS iam_role_permissions;
DROP TABLE IF EXISTS iam_permissions;

-- 2. Add subscription fields to accounts table
ALTER TABLE accounts
    ADD COLUMN subscription_type VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD COLUMN premium_until TIMESTAMP NULL;

-- 3. Seed default roles (ADMIN, USER) if not already present
INSERT INTO iam_roles (id, code, name, description, active, created_at)
SELECT gen_random_uuid(), 'ADMIN', 'Administrator', 'System administrator with full access', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM iam_roles WHERE code = 'ADMIN');

INSERT INTO iam_roles (id, code, name, description, active, created_at)
SELECT gen_random_uuid(), 'USER', 'User', 'Standard user', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM iam_roles WHERE code = 'USER');
