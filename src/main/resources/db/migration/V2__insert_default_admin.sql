-- =============================================
-- 6. Dữ liệu mẫu (Seed Data)
-- =============================================
-- Users
INSERT INTO users (id, email, username, password, full_name, status, is_deleted) VALUES 
(1, 'admin@kindergarten.com', 'admin', '$2a$12$lWbBCRxBSYwWcvn58FB.C.dvdfC5Oo89iBAUQrm5xhnyGweriptxi', 'Super Admin', 'ACTIVE', FALSE),
(2, 'teacher@kindergarten.com', 'teacher_hoa', '$2a$12$lWbBCRxBSYwWcvn58FB.C.dvdfC5Oo89iBAUQrm5xhnyGweriptxi', 'Cô Giáo Hoa', 'ACTIVE', FALSE);

-- Roles
INSERT INTO user_roles (user_id, role) VALUES 
(1, 'ADMIN'), (1, 'TEACHER'), -- Admin kiêm giáo viên
(2, 'TEACHER');

-- -- Categories
-- INSERT INTO categories (id, name, slug) VALUES
-- (1, 'Giáo án điện tử', 'giao-an-dien-tu'),
-- (2, 'Video bài giảng', 'video-bai-giang');
--
-- -- Topics
-- INSERT INTO topics (id, name, slug, category_id) VALUES
-- (1, 'Chủ đề Gia đình', 'chu-de-gia-dinh', 1),
-- (2, 'Chủ đề Thế giới thực vật', 'chu-de-thuc-vat', 1),
-- (3, 'Hoạt động thể chất', 'hoat-dong-the-chat', 2);