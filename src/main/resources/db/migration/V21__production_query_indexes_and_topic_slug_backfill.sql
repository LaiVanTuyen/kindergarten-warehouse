-- Backfill topic slugs for older rows created before slug generation was enforced.
UPDATE topics
SET slug = CONCAT('topic-', id)
WHERE slug IS NULL OR slug = '';

-- Hot-path indexes for portal/admin resource listing, owner listing, comments, favorites and audit export.
SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resources'
       AND INDEX_NAME = 'idx_resources_portal_lookup') = 0,
    'CREATE INDEX idx_resources_portal_lookup ON resources (is_deleted, visibility, status, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resources'
       AND INDEX_NAME = 'idx_resources_owner_lookup') = 0,
    'CREATE INDEX idx_resources_owner_lookup ON resources (created_by, is_deleted, status, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resources'
       AND INDEX_NAME = 'idx_resources_topic_lookup') = 0,
    'CREATE INDEX idx_resources_topic_lookup ON resources (topic_id, is_deleted, visibility, status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topics'
       AND INDEX_NAME = 'idx_topics_category_state') = 0,
    'CREATE INDEX idx_topics_category_state ON topics (category_id, is_deleted, is_active)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comments'
       AND INDEX_NAME = 'idx_comments_resource_created') = 0,
    'CREATE INDEX idx_comments_resource_created ON comments (resource_id, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorites'
       AND INDEX_NAME = 'idx_favorites_resource') = 0,
    'CREATE INDEX idx_favorites_resource ON favorites (resource_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log'
       AND INDEX_NAME = 'idx_audit_log_timestamp') = 0,
    'CREATE INDEX idx_audit_log_timestamp ON audit_log (timestamp)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
