-- Add is_deleted column to categories table
ALTER TABLE categories ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
CREATE INDEX idx_categories_is_deleted ON categories(is_deleted);

-- Add is_deleted column to topics table
ALTER TABLE topics ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
CREATE INDEX idx_topics_is_deleted ON topics(is_deleted);
