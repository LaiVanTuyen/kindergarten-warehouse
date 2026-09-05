# Thiết kế migration: tách `ratings` khỏi `comments`

Trạng thái: **CHỐT** thiết kế. Chưa triển khai — dự kiến Tuần 3.
Quy tắc nghiệp vụ liên quan: [BUSINESS_RULES_V1.md §7](BUSINESS_RULES_V1.md).

---

## 1. Hiện trạng

Rating đang là một cột của bảng `comments` (migration V6):

```sql
CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    rating INT DEFAULT 5,      -- ← vấn đề nằm ở đây
    user_id BIGINT NOT NULL,
    resource_id CHAR(36) NOT NULL,
    ...
);
```

`averageRating` trên `resources` được tính lại từ bảng này qua
`CommentRepository.recalculateAverageRating`, gọi ở `CommentServiceImpl:86`.

### Ba vấn đề

1. **Một người bình luận N lần thì có N điểm.** Không có ràng buộc duy nhất
   nào theo `(user_id, resource_id)`.
2. **Rating mặc định 5 khi client bỏ trống.** `CreateCommentRequest.rating`
   cho phép `null` và controller điền `5`. Nghĩa là một bình luận thuần túy
   không kèm đánh giá vẫn đẩy điểm trung bình lên 5.
3. **Xóa bình luận là mất rating.** Hai hành vi lẽ ra độc lập lại dính nhau.

---

## 2. Khối lượng dữ liệu cần chuyển

Kiểm tra trên Local ngày 2026-09-05:

```
tong_comment  cap_user_resource  rating_bang_5  rating_khac_5
0             0                  NULL           NULL
```

**Local hiện chưa có bình luận nào.** Demo vừa dựng lại nên cũng rỗng.

Tuy vậy migration vẫn phải viết đầy đủ phần backfill, vì tới lúc chạy thật
(Tuần 3, và sau đó trên Demo/Production) có thể đã phát sinh dữ liệu. Migration
không được giả định bảng rỗng.

---

## 3. Schema đích

```sql
CREATE TABLE ratings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    resource_id CHAR(36) NOT NULL,
    score       TINYINT  NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_rating_user_resource UNIQUE (user_id, resource_id),
    CONSTRAINT ck_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_rating_user     FOREIGN KEY (user_id)
        REFERENCES users(id)     ON DELETE CASCADE,
    CONSTRAINT fk_rating_resource FOREIGN KEY (resource_id)
        REFERENCES resources(id) ON DELETE CASCADE
);

CREATE INDEX idx_ratings_resource ON ratings (resource_id);
```

`UNIQUE(user_id, resource_id)` là ràng buộc trung tâm — nó thực thi quy tắc
"mỗi người một rating" ở tầng database, không phụ thuộc code.

`ON DELETE CASCADE` theo `resource_id` khớp với cách `favorites` và `comments`
đang làm.

---

## 4. Xử lý dữ liệu cũ

### 4.1 Vấn đề không thể giải quyết trọn vẹn

Vì `rating` mặc định là `5` khi client bỏ trống, **không thể phân biệt** trong
dữ liệu cũ đâu là "người dùng thật sự cho 5 sao" và đâu là "người dùng chỉ
bình luận, hệ thống tự điền 5".

Chuyển hết sang `ratings` sẽ mang theo toàn bộ điểm 5 giả này và làm
`averageRating` bị thổi lên.

### 4.2 Quyết định

**Chuyển toàn bộ, chọn bản ghi mới nhất theo cặp `(user_id, resource_id)`.**

Lý do chọn phương án này thay vì bỏ hết và cho đánh giá lại từ đầu: dữ liệu
hiện tại rỗng, nên rủi ro thực tế bằng không. Nếu tới lúc chạy migration mà
`comments` đã có nhiều dữ liệu thật, hãy đọc lại mục 4.3 trước khi chạy.

```sql
INSERT INTO ratings (user_id, resource_id, score, created_at, updated_at)
SELECT c.user_id,
       c.resource_id,
       c.rating,
       c.created_at,
       c.updated_at
FROM comments c
INNER JOIN (
    SELECT user_id, resource_id, MAX(id) AS keep_id
    FROM comments
    WHERE rating IS NOT NULL
    GROUP BY user_id, resource_id
) latest
  ON latest.keep_id = c.id
WHERE c.rating BETWEEN 1 AND 5;
```

Dùng `MAX(id)` thay vì `MAX(created_at)` vì `created_at` có thể trùng nhau
trong cùng một giây, còn `id` thì luôn duy nhất.

### 4.3 Nếu tới lúc chạy mà dữ liệu đã nhiều

Chạy trước để đánh giá mức độ nhiễu:

```sql
SELECT COUNT(*)                                AS tong_comment,
       COUNT(DISTINCT user_id, resource_id)    AS cap_user_resource,
       SUM(rating = 5)                         AS rating_bang_5,
       SUM(rating <> 5)                        AS rating_khac_5
FROM comments;
```

Nếu `rating_khac_5` gần bằng 0 thì gần như toàn bộ điểm là giá trị mặc định,
không phải đánh giá thật. Khi đó nên **không backfill**, để `ratings` rỗng và
đặt lại `averageRating = 0`, rồi thông báo cho người dùng đánh giá lại. Dữ
liệu rỗng trung thực hơn dữ liệu sai.

### 4.4 Bỏ cột `rating` khỏi `comments`

**Không bỏ ngay trong cùng migration.** Giữ lại một nhịp:

- V23: tạo `ratings`, backfill, tính lại `averageRating`.
- Triển khai code đọc/ghi từ `ratings`.
- Sau khi chạy ổn định ít nhất một đợt phát hành, mới ra V24 để
  `ALTER TABLE comments DROP COLUMN rating`.

Làm vậy để nếu phải rollback code thì dữ liệu cũ vẫn còn nguyên.

---

## 5. Tính lại `averageRating`

```sql
UPDATE resources r
LEFT JOIN (
    SELECT resource_id, AVG(score) AS avg_score
    FROM ratings
    GROUP BY resource_id
) agg ON agg.resource_id = r.id
SET r.average_rating = COALESCE(agg.avg_score, 0.00);
```

Cột `average_rating` là `DECIMAL(3,2)` — giữ nguyên, không cần đổi kiểu.

---

## 6. Thay đổi phía code

| Vị trí | Việc phải làm |
|---|---|
| `entity/Rating.java` | Tạo mới |
| `repository/RatingRepository.java` | Tạo mới, kèm `recalculateAverageRating` chuyển từ `CommentRepository` |
| `CommentRepository` | Bỏ `getAverageRatingByResourceId` và `recalculateAverageRating` |
| `CommentServiceImpl:86` | Bỏ lời gọi tính lại rating |
| `CreateCommentRequest` | Bỏ trường `rating` |
| `CommentResponse` | Bỏ `rating`; FE lấy rating từ endpoint riêng |
| `RatingController` | Tạo mới — xem API_CONTRACT_V2 §Ratings |
| FE `comment.service.ts` | Tách phần rating sang service riêng |

Điểm cần chú ý: sau khi tách, **xóa bình luận không được đụng tới `ratings`**.
Đây là một trong các trường hợp phải có test.

---

## 7. Kiểm thử bắt buộc trước khi merge

- [ ] Một người đánh giá hai lần → chỉ còn một bản ghi, điểm được cập nhật
- [ ] `UNIQUE(user_id, resource_id)` chặn được insert trùng ở tầng DB
- [ ] Bình luận không kèm rating → không tạo bản ghi trong `ratings`
- [ ] Xóa bình luận → rating vẫn còn
- [ ] Xóa tài nguyên → rating bị xóa theo (cascade)
- [ ] `averageRating` khớp với `AVG(score)` sau mỗi thao tác
- [ ] Tài nguyên chưa ai đánh giá → `averageRating = 0`, không phải `null`
- [ ] GUEST gọi API đánh giá → 401, không phải 500
- [ ] Chạy migration trên bản sao DB có dữ liệu → không mất bình luận nào
