-- Add is_active column to categories table
ALTER TABLE categories ADD COLUMN is_active BOOLEAN DEFAULT TRUE;
CREATE INDEX idx_categories_is_active ON categories(is_active);

-- Add is_active column to topics table
ALTER TABLE topics ADD COLUMN is_active BOOLEAN DEFAULT TRUE;
CREATE INDEX idx_topics_is_active ON topics(is_active);
