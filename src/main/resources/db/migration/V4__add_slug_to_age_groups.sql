-- Add slug column to age_groups table
ALTER TABLE age_groups ADD COLUMN slug VARCHAR(100);

-- Update existing records with slugs
UPDATE age_groups SET slug = '3-4-tuoi' WHERE name LIKE '%3-4%';
UPDATE age_groups SET slug = '4-5-tuoi' WHERE name LIKE '%4-5%';
UPDATE age_groups SET slug = '5-6-tuoi' WHERE name LIKE '%5-6%';

-- Make slug required and unique after population
ALTER TABLE age_groups MODIFY COLUMN slug VARCHAR(100) NOT NULL;
ALTER TABLE age_groups ADD CONSTRAINT uk_age_groups_slug UNIQUE (slug);
