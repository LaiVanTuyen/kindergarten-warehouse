-- =========================================================
-- V28: Giai doan 3 — phoi hop trong truong.
--   * Duyet chuyen mon cho ke hoach (review_status):
--       NONE -> PENDING (chu gui duyet, ke hoach phai SHARED)
--       PENDING -> APPROVED | CHANGES_REQUESTED (ADMIN, kem phien ban da xem)
--       APPROVED -> NONE khi chu sua NOI DUNG (can duyet lai)
--     Cap nhat bang UPDATE co dieu kien nen khong tang version cua ke hoach.
--   * Gop y cua dong nghiep (plan_comments).
--   * Danh dau "da su dung" (plan_usages): moi giao vien mot dong / ke hoach.
-- =========================================================

ALTER TABLE lesson_plans
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN review_note VARCHAR(1000) NULL,
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at TIMESTAMP NULL,
    ADD INDEX idx_lesson_plans_review (review_status, updated_at),
    ADD CONSTRAINT fk_lesson_plans_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE plan_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_comments_plan_time (plan_id, created_at),
    CONSTRAINT fk_plan_comments_plan FOREIGN KEY (plan_id) REFERENCES lesson_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE plan_usages (
    plan_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    used_count INT NOT NULL DEFAULT 1,
    last_used_on DATE NOT NULL,
    PRIMARY KEY (plan_id, user_id),
    CONSTRAINT fk_plan_usages_plan FOREIGN KEY (plan_id) REFERENCES lesson_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_usages_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
