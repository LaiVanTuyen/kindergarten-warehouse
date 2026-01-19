-- Add slug column to resources table
ALTER TABLE resources ADD COLUMN slug VARCHAR(255);

-- Update existing records with default slugs (using ID to ensure uniqueness)
-- In a real scenario, we might want a better slug generation strategy, but this ensures non-null and unique values.
UPDATE resources SET slug = CONCAT('resource-', id);

-- Make slug required and unique
ALTER TABLE resources MODIFY COLUMN slug VARCHAR(255) NOT NULL;
ALTER TABLE resources ADD CONSTRAINT uk_resources_slug UNIQUE (slug);
