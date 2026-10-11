-- =========================================================
-- V24: Tinh nang tuong tu tailieu.vn
--  1. Tag cho tai nguyen (resource_tags)
--  2. (Tim kiem khong dau dung LIKE + collation utf8mb4_0900_ai_ci; FULLTEXT ngram
--     da thu va KHONG khop 'bai hat' voi 'Bai hat' co dau nen khong dung.)
--  3. Lich su tai ve cua nguoi dung (download_history)
--  4. Bao cao vi pham (resource_reports)
--  5. Thong bao trong ung dung (notifications)
-- =========================================================

CREATE TABLE resource_tags (
    resource_id CHAR(36) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (resource_id, tag),
    INDEX idx_resource_tags_tag (tag),
    CONSTRAINT fk_resource_tags_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

CREATE TABLE download_history (
    user_id BIGINT NOT NULL,
    resource_id CHAR(36) NOT NULL,
    download_count INT NOT NULL DEFAULT 1,
    last_downloaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, resource_id),
    INDEX idx_download_history_user_time (user_id, last_downloaded_at),
    CONSTRAINT fk_download_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_download_history_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

CREATE TABLE resource_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id CHAR(36) NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(1000),
    resolved_by BIGINT,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_resource_reports_status_time (status, created_at),
    INDEX idx_resource_reports_resource (resource_id),
    CONSTRAINT fk_resource_reports_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_reports_resolver FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000),
    link VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notifications_user_read_time (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
