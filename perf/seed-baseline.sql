-- ===========================================================
-- Dataset cố định cho baseline k6
-- ===========================================================
-- Chạy trên DB DEMO (warehouse_demo_db). KHÔNG chạy trên Local.
--
--   docker exec -i warehouse_demo_mysql \
--     mysql -uroot -p<pass> warehouse_demo_db < perf/seed-baseline.sql
--
-- Dataset phải GIỐNG HỆT khi đo lại ở Tuần 7, nếu không hai lần đo
-- không so sánh được. Mọi giá trị đều tính từ biến đếm n — không dùng
-- RAND() — nên chạy lại luôn cho ra cùng một tập dữ liệu.
--
-- Thành phần: 2.000 resources
--   1.700  PUBLIC   + APPROVED   → Portal thấy
--     200  PRIVATE  + APPROVED   → Portal phải lọc bỏ
--     100  PUBLIC   + PENDING    → Portal phải lọc bỏ
-- Giữ tỉ lệ có dữ liệu bị lọc để truy vấn phải làm việc thật,
-- thay vì quét một bảng mà mọi dòng đều khớp.
-- ===========================================================

SET SESSION cte_max_recursion_depth = 20000;

DELETE FROM resource_age_groups
 WHERE resource_id IN (SELECT id FROM resources WHERE slug LIKE 'perf-seed-%');
DELETE FROM resources WHERE slug LIKE 'perf-seed-%';

INSERT INTO resources (
    id, title, slug, description, file_url, thumbnail_url,
    file_type, file_extension, file_size,
    views_count, average_rating, download_count,
    topic_id, visibility, status, is_deleted,
    created_by, resource_type, version, created_at, updated_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2000
),
t AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY id) - 1) AS rn,
           COUNT(*) OVER () AS total
    FROM topics
)
SELECT
    UUID(),
    CONCAT('Học liệu mẫu ', LPAD(n, 4, '0'), ' cho đo hiệu năng'),
    CONCAT('perf-seed-', LPAD(n, 4, '0')),
    CONCAT('Mô tả học liệu mẫu số ', n,
           '. Nội dung sinh tự động để đo hiệu năng, không phải tài liệu thật.'),
    CONCAT('/warehouse-demo-bucket/resources/files/perf-seed-', n, '.pdf'),
    CONCAT('/warehouse-demo-bucket/resources/thumbnails/perf-seed-', n, '.png'),
    'PDF', 'pdf',
    1048576 + (n * 137),
    (n * 7919)   MOD 5000,                        -- views_count  0..4999
    ROUND(1 + ((n * 31) MOD 400) / 100, 2),       -- average_rating 1.00..4.99
    (n * 104729) MOD 800,                         -- download_count 0..799
    t.id,
    CASE WHEN n > 1700 AND n <= 1900 THEN 'PRIVATE' ELSE 'PUBLIC' END,
    CASE WHEN n > 1900               THEN 'PENDING' ELSE 'APPROVED' END,
    0,
    (SELECT MIN(id) FROM users),
    'FILE',
    0,
    NOW() - INTERVAL (n MOD 365) DAY,
    NOW() - INTERVAL (n MOD 365) DAY
FROM seq
JOIN t ON t.rn = (seq.n MOD t.total);

-- Gán nhóm tuổi: mỗi tài nguyên 1–2 nhóm, phân bố đều để filter có việc làm
INSERT INTO resource_age_groups (resource_id, age_group_id)
SELECT r.id, 1 + (CAST(SUBSTRING(r.slug, 11) AS UNSIGNED) MOD 3)
FROM resources r
WHERE r.slug LIKE 'perf-seed-%';

INSERT INTO resource_age_groups (resource_id, age_group_id)
SELECT r.id, 1 + ((CAST(SUBSTRING(r.slug, 11) AS UNSIGNED) + 1) MOD 3)
FROM resources r
WHERE r.slug LIKE 'perf-seed-%'
  AND CAST(SUBSTRING(r.slug, 11) AS UNSIGNED) MOD 2 = 0;

SELECT 'Đã seed' AS ket_qua,
       COUNT(*) AS tong,
       SUM(visibility = 'PUBLIC'  AND status = 'APPROVED') AS portal_thay,
       SUM(visibility = 'PRIVATE')                         AS private_bi_loc,
       SUM(status     = 'PENDING')                         AS pending_bi_loc
FROM resources WHERE slug LIKE 'perf-seed-%';
