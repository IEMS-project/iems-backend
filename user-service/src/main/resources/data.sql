-- IEMS Backend - User Service Seed Data
-- This script creates initial user profiles for admin accounts

-- Insert Admin User Profiles
INSERT INTO users (id, first_name, last_name, email, address, phone, dob, gender, personal_id, image, bank_account_number, bank_name, contract_type, start_date, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000001', 'System', 'Administrator', 'admin@iems.com', '123 Admin Street, Ho Chi Minh City', '0123456789', '1990-01-01', 'MALE', '123456789001', null, '1234567890', 'Vietcombank', 'FULL_TIME', '2024-01-01', NOW(), NOW()),
('00000000-0000-0000-0000-000000000002', 'Regular', 'Administrator', 'admin2@iems.com', '456 Admin Avenue, Ho Chi Minh City', '0987654321', '1990-02-01', 'FEMALE', '123456789002', null, '0987654321', 'BIDV', 'FULL_TIME', '2024-01-01', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

