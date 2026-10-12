-- OAuth users table schema
-- Run this SQL script to create the users table in your PostgreSQL database

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    name VARCHAR(255),
    profile_image_url TEXT,
    gportal_user_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_user_id)
);

-- gportal_user_id: MCP 서버(gportal 등)에 X-User-Id 헤더로 전달할 값 (예: "U000001")
CREATE INDEX IF NOT EXISTS idx_users_provider_user_id ON users(provider, provider_user_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_gportal_user_id ON users(gportal_user_id);
