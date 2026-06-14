-- =========================================================
-- V21: Thêm cột version cho JPA @Version (optimistic lock) trên resources.
-- Phát hiện xung đột khi 2 transaction đồng thời sửa cùng 1 resource row
-- (ví dụ 2 admin cùng cập nhật/duyệt). Liên quan DESIGN_REVIEW [DB-6]/[ARC-4].
-- Lưu ý: các UPDATE đếm view/download và recalc average_rating dùng bulk JPQL
-- nên KHÔNG tăng version → không gây xung đột với luồng chỉnh sửa.
-- =========================================================

ALTER TABLE resources
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
