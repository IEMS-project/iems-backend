-- ============================================
-- IAM Database - Clean & Reinitialize Script
-- Run on iam-db database
-- Xóa toàn bộ data và tạo lại với schema mới
-- ============================================

-- STEP 1: Drop all data
TRUNCATE TABLE iam_user_permissions CASCADE;
TRUNCATE TABLE iam_user_roles CASCADE;
TRUNCATE TABLE iam_role_permissions CASCADE;
TRUNCATE TABLE iam_permissions CASCADE;
TRUNCATE TABLE iam_roles CASCADE;
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE accounts CASCADE;

-- STEP 2: Drop old columns nếu còn
ALTER TABLE accounts DROP COLUMN IF EXISTS user_id;
ALTER TABLE users DROP COLUMN IF EXISTS personal_id;
ALTER TABLE users DROP COLUMN IF EXISTS bank_account_number;
ALTER TABLE users DROP COLUMN IF EXISTS bank_name;
ALTER TABLE users DROP COLUMN IF EXISTS contract_type;
ALTER TABLE users DROP COLUMN IF EXISTS start_date;
ALTER TABLE users DROP COLUMN IF EXISTS role;

-- STEP 3: Add account_id column to users if not exists
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_id UUID;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'users_account_id_unique'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT users_account_id_unique UNIQUE (account_id);
    END IF;
END $$;

-- STEP 4: Drop unique constraint on phone nếu có
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key;

-- STEP 5: Update column types
-- Make created_at and updated_at NOT NULL with defaults
ALTER TABLE users 
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE users 
    ALTER COLUMN updated_at DROP NOT NULL;

-- Verify schema
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'users'
ORDER BY ordinal_position;

SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'accounts'
ORDER BY ordinal_position;
