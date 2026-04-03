-- 1. Add status column (PENDING, APPROVED, REJECTED)
ALTER TABLE resources 
ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING' NOT NULL AFTER is_active;

-- 2. Add download_count column
ALTER TABLE resources 
ADD COLUMN download_count BIGINT DEFAULT 0 NULL AFTER views_count;

-- 3. Add average_rating column for caching
ALTER TABLE resources 
ADD COLUMN average_rating DECIMAL(3, 2) DEFAULT 0.00 NULL AFTER views_count;
