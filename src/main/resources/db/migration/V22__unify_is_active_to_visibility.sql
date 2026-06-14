-- =========================================================
-- V22 (D1): Hợp nhất is_active (boolean) -> visibility (enum PUBLIC/PRIVATE)
-- cho categories, topics, banners — đồng bộ với resources.
-- Mapping: is_active = 1 -> PUBLIC, is_active = 0 -> PRIVATE.
-- Tham chiếu DESIGN_REVIEW [DB-1] & API_CONTRACT_V1 §3.1.
-- =========================================================

-- categories
ALTER TABLE categories ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
UPDATE categories SET visibility = CASE WHEN is_active = 1 THEN 'PUBLIC' ELSE 'PRIVATE' END;
ALTER TABLE categories DROP COLUMN is_active;

-- topics
ALTER TABLE topics ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
UPDATE topics SET visibility = CASE WHEN is_active = 1 THEN 'PUBLIC' ELSE 'PRIVATE' END;
ALTER TABLE topics DROP COLUMN is_active;

-- banners
ALTER TABLE banners ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
UPDATE banners SET visibility = CASE WHEN is_active = 1 THEN 'PUBLIC' ELSE 'PRIVATE' END;
ALTER TABLE banners DROP COLUMN is_active;
