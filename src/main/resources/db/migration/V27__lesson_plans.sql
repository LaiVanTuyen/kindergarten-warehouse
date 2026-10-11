-- =========================================================
-- V27: Ke hoach hoat dong (lesson plans) — giai doan 1.
--
-- Luu CO CAU TRUC de giai doan 2 (tro ly AI) sinh/sua tung phan:
--   * cac phan chu (muc tieu, chuan bi, dieu chinh, ghi nhan) la cot rieng;
--   * moi buoc tien trinh la mot dong, co cot source (TEACHER | AI_SUGGESTED)
--     de luon phan biet noi dung giao vien viet voi noi dung AI de xuat;
--   * hoc lieu gan voi tung buoc chi la THAM CHIEU toi resources, quyen xem
--     kiem lai moi lan doc (giong bo hoc lieu).
--
-- status: DRAFT (chi chu so huu) | SHARED (giao vien khac xem/sao chep).
--   Giai doan 3 se them APPROVED (duyet chuyen mon).
-- is_template: ke hoach mau (ADMIN danh dau), moi giao vien xem/sao chep.
-- source_plan_id: sao chep tu ke hoach nao (mau, ke hoach cu, dong nghiep).
-- =========================================================

CREATE TABLE lesson_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    age_group_id BIGINT NULL,
    topic_id BIGINT NULL,
    duration_minutes INT NULL,
    class_size INT NULL,
    objectives TEXT,
    preparation TEXT,
    adaptations TEXT,
    reflection TEXT,
    taught_on DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    is_template BOOLEAN NOT NULL DEFAULT FALSE,
    source_plan_id BIGINT NULL,
    collection_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lesson_plans_owner_updated (owner_id, updated_at),
    INDEX idx_lesson_plans_status_updated (status, updated_at),
    INDEX idx_lesson_plans_template (is_template, updated_at),
    CONSTRAINT fk_lesson_plans_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_plans_age_group FOREIGN KEY (age_group_id) REFERENCES age_groups(id) ON DELETE SET NULL,
    CONSTRAINT fk_lesson_plans_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE SET NULL,
    CONSTRAINT fk_lesson_plans_source FOREIGN KEY (source_plan_id) REFERENCES lesson_plans(id) ON DELETE SET NULL,
    CONSTRAINT fk_lesson_plans_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE SET NULL
);

CREATE TABLE lesson_plan_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    position INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    duration_minutes INT NULL,
    description TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'TEACHER',
    INDEX idx_lesson_plan_steps_order (plan_id, position),
    CONSTRAINT fk_lesson_plan_steps_plan FOREIGN KEY (plan_id) REFERENCES lesson_plans(id) ON DELETE CASCADE
);

CREATE TABLE lesson_plan_step_resources (
    step_id BIGINT NOT NULL,
    position INT NOT NULL,
    resource_id CHAR(36) NOT NULL,
    PRIMARY KEY (step_id, position),
    INDEX idx_lesson_plan_step_resources_resource (resource_id),
    CONSTRAINT fk_lesson_plan_step_resources_step FOREIGN KEY (step_id) REFERENCES lesson_plan_steps(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_plan_step_resources_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
);
