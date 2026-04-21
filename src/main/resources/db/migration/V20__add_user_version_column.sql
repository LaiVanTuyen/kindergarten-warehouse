-- =========================================================
-- V20: Thêm cột version cho JPA @Version (optimistic lock).
-- Giúp phát hiện xung đột khi 2 transaction đồng thời sửa cùng 1 user row
-- (ví dụ 2 admin block nhau cùng lúc).
-- =========================================================

ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
