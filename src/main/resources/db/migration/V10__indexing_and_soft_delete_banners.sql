ALTER TABLE banners ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_banner_active_sort ON banners(is_active, display_order);
CREATE INDEX idx_banner_platform ON banners(platform);
