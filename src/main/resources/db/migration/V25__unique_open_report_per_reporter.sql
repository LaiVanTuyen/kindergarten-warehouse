-- =========================================================
-- V25: Moi nguoi chi co MOT bao cao OPEN cho moi tai lieu.
--
-- Kiem tra exists() o service khong chan duoc hai request dong thoi (ca hai
-- deu doc "chua co" truoc khi ben kia commit). Rang buoc phai nam o DB.
--
-- open_marker = 1 khi OPEN, NULL khi da xu ly. UNIQUE cho phep nhieu NULL,
-- nen bao cao da dong khong chan nguoi dung bao cao lai ve sau.
-- =========================================================

ALTER TABLE resource_reports
    ADD COLUMN open_marker TINYINT
        GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_resource_reports_open (resource_id, reporter_id, open_marker);
