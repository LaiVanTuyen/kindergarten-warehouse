-- =========================================================
-- V26: Bo hoc lieu (resource collections) — giai doan 1.
--
-- Bo chi luu THAM CHIEU toi resources, khong chep tep. Quyen xem tung tai
-- lieu duoc kiem lai moi lan doc, nen tai lieu bi go/an se hien "khong con
-- kha dung" thay vi lo noi dung.
--
-- source_collection_id: bo duoc sao chep tu bo nao (SET NULL khi bo goc bi
-- xoa). Ke hoach hoat dong (giai doan sau) dung cung co che sao chep nay.
-- =========================================================

CREATE TABLE collections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    age_group_id BIGINT NULL,
    topic_id BIGINT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    source_collection_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_collections_owner_updated (owner_id, updated_at),
    INDEX idx_collections_visibility_updated (visibility, updated_at),
    CONSTRAINT fk_collections_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_collections_age_group FOREIGN KEY (age_group_id) REFERENCES age_groups(id) ON DELETE SET NULL,
    CONSTRAINT fk_collections_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE SET NULL,
    CONSTRAINT fk_collections_source FOREIGN KEY (source_collection_id) REFERENCES collections(id) ON DELETE SET NULL
);

CREATE TABLE collection_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collection_id BIGINT NOT NULL,
    resource_id CHAR(36) NOT NULL,
    position INT NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_collection_items_resource (collection_id, resource_id),
    INDEX idx_collection_items_order (collection_id, position),
    CONSTRAINT fk_collection_items_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_items_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
);
