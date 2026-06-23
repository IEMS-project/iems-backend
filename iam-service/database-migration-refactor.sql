-- ============================================
-- IAM Database Migration - Refactor User/Account Relationship
-- Run on iam-db database
-- ============================================

-- **KIẾN TRÚC MỚI:**
-- - Account: chứa username, password, và roles/permissions
-- - User: chứa profile information, reference tới Account qua account_id  
-- - UserRole.user_id và UserPermission.user_id giờ sẽ lưu account_id

-- STEP 1: Thêm column account_id vào bảng users
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_id UUID;

-- STEP 2: Migrate dữ liệu: với mỗi user hiện tại, tạo account tương ứng
-- (Chỉ chạy nếu bạn có data cũ từ user-service cần migrate)
-- Nếu bạn bắt đầu từ đầu, skip step này

-- STEP 3: Add unique constraint cho account_id trong users
ALTER TABLE users ADD CONSTRAINT users_account_id_unique UNIQUE (account_id);

-- STEP 4: Drop column user_id trong bảng accounts (nếu còn)
-- Trước khi drop, cần update các UserRole và UserPermission references
-- UserRole.user_id và UserPermission.user_id giờ sẽ là account_id

-- Backup first (optional)
-- CREATE TABLE accounts_backup AS SELECT * FROM accounts;

ALTER TABLE accounts DROP COLUMN IF EXISTS user_id;

-- STEP 5: Drop các column không cần trong users
ALTER TABLE users DROP COLUMN IF EXISTS personal_id;
ALTER TABLE users DROP COLUMN IF EXISTS bank_account_number;
ALTER TABLE users DROP COLUMN IF EXISTS bank_name;
ALTER TABLE users DROP COLUMN IF EXISTS contract_type;
ALTER TABLE users DROP COLUMN IF EXISTS start_date;
ALTER TABLE users DROP COLUMN IF EXISTS role;

-- STEP 6: Update timestamps type
ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at::timestamp with time zone;
ALTER TABLE users ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at::timestamp with time zone;

-- Make phone non-unique
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key;

-- STEP 7: Verify schema
SELECT 
    table_name,
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name IN ('accounts', 'users')
ORDER BY table_name, ordinal_position;

-- STEP 8: Verify data integrity
SELECT 
    'Accounts without user profile' as info,
    COUNT(*) as count
FROM accounts a
LEFT JOIN users u ON u.account_id = a.id
WHERE u.id IS NULL

UNION ALL

SELECT 
    'Users without account' as info,
    COUNT(*) as count
FROM users u
WHERE u.account_id IS NULL;
