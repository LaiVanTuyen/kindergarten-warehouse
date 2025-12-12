-- =============================================
-- 1. USERS & ROLES (Đã chuẩn hóa)
-- =============================================
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', 
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_is_deleted (is_deleted)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL, -- Enum: ADMIN, TEACHER, USER
    PRIMARY KEY (user_id, role), -- Khóa chính kép để tránh duplicate role cho 1 user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- =============================================
-- 2. CATEGORIES (Danh mục lớn)
-- =============================================
DROP TABLE IF EXISTS categories;
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(150) NOT NULL UNIQUE, -- Dùng cho URL thân thiện
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- 3. TOPICS (Chủ đề con)
-- =============================================
DROP TABLE IF EXISTS topics;
CREATE TABLE topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(150) UNIQUE, -- [NEW] Thêm slug cho URL đẹp
    description TEXT,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

-- =============================================
-- 4. BANNERS (Quảng cáo/Slide)
-- =============================================
DROP TABLE IF EXISTS banners;
CREATE TABLE banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL,
    link VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- 5. RESOURCES (Tài nguyên số - Core)
-- =============================================
DROP TABLE IF EXISTS resources;
CREATE TABLE resources (
    id CHAR(36) PRIMARY KEY, -- UUID
    title VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- File Info
    file_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500), -- [NEW] Ảnh đại diện cho video/tài liệu
    file_type VARCHAR(20),      -- VIDEO, DOCUMENT, EXCEL, PDF
    file_extension VARCHAR(10), -- .mp4, .pdf
    file_size BIGINT,           -- Kích thước file (bytes)
    
    -- Stats & Relations
    views_count BIGINT DEFAULT 0,
    topic_id BIGINT NOT NULL,
    created_by_id BIGINT,
    
    -- Status & Audit
    is_active BOOLEAN DEFAULT TRUE,  -- Ẩn/Hiện trên web
    is_deleted BOOLEAN DEFAULT FALSE, -- [NEW] Soft Delete cho tài nguyên
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Constraints & Indexes
    CONSTRAINT fk_resource_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_user FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_resource_topic (topic_id),
    INDEX idx_resource_active (is_active)
);

