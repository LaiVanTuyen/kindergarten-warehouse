-- Add the new column visibility
ALTER TABLE resources ADD COLUMN visibility VARCHAR(20) DEFAULT 'PUBLIC' AFTER topic_id;

-- Migrate existing data: if is_active is true -> PUBLIC, else -> PRIVATE
UPDATE resources SET visibility = 'PUBLIC' WHERE is_active = true;
UPDATE resources SET visibility = 'PRIVATE' WHERE is_active = false;
UPDATE resources SET visibility = 'PUBLIC' WHERE is_active IS NULL;

-- Make the new column not null after data migration
ALTER TABLE resources MODIFY COLUMN visibility VARCHAR(20) NOT NULL;

-- Remove the old is_active column
ALTER TABLE resources DROP COLUMN is_active;
