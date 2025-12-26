-- =============================================
-- 7. AGE GROUPS (Nhóm tuổi)
-- =============================================
CREATE TABLE age_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE, -- E.g., "Mầm (3-4 tuổi)"
    min_age INT,
    max_age INT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- 8. RESOURCE_AGE_GROUPS (Many-to-Many)
-- =============================================
CREATE TABLE resource_age_groups (
    resource_id CHAR(36) NOT NULL,
    age_group_id BIGINT NOT NULL,
    PRIMARY KEY (resource_id, age_group_id),
    CONSTRAINT fk_rag_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
    CONSTRAINT fk_rag_age_group FOREIGN KEY (age_group_id) REFERENCES age_groups(id) ON DELETE CASCADE
);

-- =============================================
-- 9. Seed Data for Age Groups
-- =============================================
INSERT INTO age_groups (name, min_age, max_age) VALUES 
('(3-4 tuổi)', 3, 4),
('(4-5 tuổi)', 4, 5),
('(5-6 tuổi)', 5, 6);
