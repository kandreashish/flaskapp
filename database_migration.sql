-- Migration script to add user_devices table for multi-device FCM support
-- Run this script to create the new table structure

CREATE TABLE IF NOT EXISTS user_devices (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    fcm_token VARCHAR(512) NOT NULL UNIQUE,
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_user_devices_user_id
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_user_devices_fcm_token ON user_devices(fcm_token);
CREATE INDEX IF NOT EXISTS idx_user_devices_user_active ON user_devices(user_id, is_active);

-- Optional: Migrate existing FCM tokens from users table to user_devices table
-- Uncomment the following lines if you want to migrate existing data

/*
INSERT INTO user_devices (id, user_id, fcm_token, device_name, device_type, created_at, updated_at, is_active)
SELECT
    RANDOM_UUID() as id,
    id as user_id,
    fcm_token,
    'Legacy Device' as device_name,
    'unknown' as device_type,
    created_at,
    updated_at,
    TRUE as is_active
FROM users
WHERE fcm_token IS NOT NULL AND fcm_token != '';
*/

-- Add onboarding_completed column to expense_users table if it does not exist
-- (Adjust table name if your actual user table differs)
ALTER TABLE expense_users ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;
-- Add low resolution profile picture column
ALTER TABLE expense_users ADD COLUMN IF NOT EXISTS profile_pic_low VARCHAR(1000);
