-- =========================================================
-- V19: Account hardening
--   - Email verification, blocked reason & timestamp
--   - Original username/email snapshot (for deterministic restore)
--   - Token version (force JWT invalidation on pwd change / block)
--   - Widen status to accept PENDING / INACTIVE
-- =========================================================

ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blocked_reason VARCHAR(255) DEFAULT NULL,
    ADD COLUMN blocked_at TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN original_username VARCHAR(50) DEFAULT NULL,
    ADD COLUMN original_email VARCHAR(100) DEFAULT NULL,
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

-- Back-fill: existing accounts were trusted before verification existed
UPDATE users SET email_verified = TRUE;

CREATE INDEX idx_users_status_deleted ON users (status, is_deleted);
