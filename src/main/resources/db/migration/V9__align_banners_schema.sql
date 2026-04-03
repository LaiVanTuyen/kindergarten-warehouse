ALTER TABLE banners
ADD COLUMN title VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Stores HTML title with span tags' AFTER id,
ADD COLUMN subtitle VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL AFTER title,
ADD COLUMN platform VARCHAR(50) DEFAULT 'WEB' AFTER subtitle,
ADD COLUMN bg_from VARCHAR(50) NOT NULL COMMENT 'Tailwind class for gradient start' AFTER image_url,
ADD COLUMN bg_to VARCHAR(50) NOT NULL COMMENT 'Tailwind class for gradient end' AFTER bg_from,
ADD COLUMN start_date TIMESTAMP NULL AFTER display_order,
ADD COLUMN end_date TIMESTAMP NULL AFTER start_date;
