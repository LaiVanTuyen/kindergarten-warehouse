ALTER TABLE resources
ADD COLUMN resource_type VARCHAR(20) DEFAULT 'FILE';

-- Update existing records to have a default type
UPDATE resources SET resource_type = 'FILE' WHERE resource_type IS NULL;

-- Add index for better performance
CREATE INDEX idx_resource_type ON resources(resource_type);
