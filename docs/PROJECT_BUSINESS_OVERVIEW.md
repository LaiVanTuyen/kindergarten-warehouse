# Tổng hợp dự án Kindergarten Warehouse

## 1. Mục tiêu dự án

Kindergarten Warehouse là hệ thống backend quản lý kho học liệu số cho trường mầm non. Hệ thống tập trung vào việc lưu trữ, phân loại, duyệt, tìm kiếm và cung cấp tài nguyên giảng dạy cho giáo viên, phụ huynh và người dùng cuối.

Các loại học liệu được hỗ trợ gồm:

- Tệp tải lên: video, tài liệu, PDF, Excel, PowerPoint, hình ảnh.
- Video YouTube.
- Liên kết ngoài theo enum `EXTERNAL_LINK`, dù luồng controller/service hiện tại tập trung chính vào file và YouTube.
- Ảnh đại diện tài nguyên, banner, icon danh mục, avatar người dùng.

Hệ thống dùng Spring Boot làm API backend, MySQL làm cơ sở dữ liệu nghiệp vụ, Redis cho OTP/cache/blacklist token/rate limit, MinIO làm kho lưu trữ file tương thích S3, Flyway để quản lý migration database.

## 2. Stack kỹ thuật

- Java 17.
- Spring Boot 3.2.0.
- Spring Web MVC.
- Spring Security + JWT.
- Spring Data JPA/Hibernate.
- Flyway migration.
- MySQL 8.
- Redis 7.
- MinIO/S3 compatible storage.
- Spring Mail.
- Spring AOP cho audit log và rate limit.
- Springdoc OpenAPI/Swagger.
- Rollbar error tracking.
- Docker Compose cho local/prod deployment.

## 3. Cấu trúc dự án

- `src/main/java/com/kindergarten/warehouse/controller`: REST API controllers.
- `src/main/java/com/kindergarten/warehouse/service`: nghiệp vụ chính.
- `src/main/java/com/kindergarten/warehouse/service/impl`: triển khai service cho category, topic, resource, comment, banner.
- `src/main/java/com/kindergarten/warehouse/entity`: entity ánh xạ database.
- `src/main/java/com/kindergarten/warehouse/repository`: Spring Data repositories.
- `src/main/java/com/kindergarten/warehouse/dto/request`: dữ liệu đầu vào API.
- `src/main/java/com/kindergarten/warehouse/dto/response`: dữ liệu trả về API.
- `src/main/java/com/kindergarten/warehouse/security`: JWT, filter xác thực, user details.
- `src/main/java/com/kindergarten/warehouse/config`: cấu hình security, Redis, MinIO, mail, seed dữ liệu, audit.
- `src/main/java/com/kindergarten/warehouse/aspect`: audit log và rate limiting.
- `src/main/java/com/kindergarten/warehouse/scheduler`: đồng bộ last active và dọn audit log.
- `src/main/resources/db/migration`: migration database.
- `src/main/resources/assets/images`: ảnh seed icon/banner.
- `docs`: tài liệu hướng dẫn triển khai và quản trị.
- `scripts`: script triển khai, kiểm tra env, tạo secret.

## 4. Vai trò người dùng

Hệ thống có 3 role:

- `ADMIN`: quản trị toàn bộ hệ thống, tài khoản, danh mục, chủ đề, banner, duyệt tài nguyên, audit log.
- `TEACHER`: giáo viên, được upload và quản lý tài nguyên của mình; được thao tác với tài nguyên theo rule service.
- `USER`: người dùng thường, xem tài nguyên public, bình luận, đánh giá, yêu thích, quản lý profile cá nhân.

Trạng thái tài khoản:

- `PENDING`: tài khoản mới đăng ký, chờ xác thực email.
- `ACTIVE`: tài khoản hoạt động bình thường.
- `BLOCKED`: bị admin khóa.
- `INACTIVE`: không hoạt động hoặc bị đặt trạng thái không hoạt động.

Quy tắc bảo vệ tài khoản quan trọng:

- Người dùng đăng ký mới không được tự động đăng nhập, phải xác thực email bằng OTP.
- Admin tạo user trực tiếp thì user được `ACTIVE` và `emailVerified=true`.
- Không cho admin tự xóa, tự khóa hoặc tự hạ quyền chính mình.
- Không cho xóa, khóa hoặc hạ quyền admin cuối cùng còn hoạt động.
- Khi đổi mật khẩu, reset mật khẩu, block user, thay đổi role hoặc xóa mềm user, hệ thống tăng `tokenVersion` để vô hiệu hóa JWT cũ.
- Xóa mềm user sẽ đổi `username/email` bằng hậu tố `_deleted_{timestamp}` và lưu bản gốc ở `original_username/original_email` để restore.

## 5. Mô hình dữ liệu chính

### 5.1. `users`

Lưu tài khoản người dùng.

Trường chính:

- `id`: khóa chính.
- `username`: tên đăng nhập duy nhất.
- `email`: email duy nhất.
- `password`: mật khẩu đã hash BCrypt.
- `full_name`: họ tên.
- `avatar_url`: URL avatar trên MinIO.
- `status`: `PENDING`, `ACTIVE`, `BLOCKED`, `INACTIVE`.
- `is_deleted`: xóa mềm.
- `email_verified`: đã xác thực email hay chưa.
- `blocked_reason`, `blocked_at`: lý do và thời điểm khóa.
- `original_username`, `original_email`: bản gốc trước khi xóa mềm.
- `token_version`: phiên bản token để vô hiệu hóa JWT cũ.
- `version`: optimistic lock của JPA.
- `phone_number`, `bio`: thông tin cá nhân.
- `last_active`: lần hoạt động gần nhất.
- `created_at`, `updated_at`, `created_by`, `updated_by`: audit metadata.

### 5.2. `user_roles`

Bảng phụ cho nhiều role của một user.

- `user_id`: FK đến `users`.
- `role`: `ADMIN`, `TEACHER`, `USER`.

### 5.3. `categories`

Danh mục lớn của kho học liệu.

Trường chính:

- `id`.
- `name`: tên danh mục.
- `slug`: URL thân thiện, duy nhất.
- `icon`: URL icon trên MinIO.
- `description`.
- `is_active`: bật/tắt hiển thị.
- `is_deleted`: xóa mềm.
- Audit metadata.

Ví dụ seed:

- Hoạt động giáo dục.
- Kho tài nguyên.
- Góc sáng tạo.
- Công tác chuyên môn.
- Góc phụ huynh.

### 5.4. `topics`

Chủ đề con thuộc một danh mục.

Trường chính:

- `id`.
- `name`.
- `slug`.
- `description`.
- `category_id`: FK đến `categories`.
- `is_active`.
- `is_deleted`.
- `resourceCount`: công thức đếm số resource chưa xóa.
- Audit metadata.

Ví dụ topic:

- Giáo án Mầm, Chồi, Lá.
- Bài giảng điện tử.
- Thư viện hình ảnh.
- Video & clip minh họa.
- Âm nhạc & bài hát.
- Tạo hình & thủ công.
- Trò chơi vận động.
- Dinh dưỡng & sức khỏe.

### 5.5. `age_groups`

Nhóm tuổi mầm non dùng để gắn vào học liệu.

Trường chính:

- `id`.
- `name`.
- `slug`.
- `min_age`.
- `max_age`.
- `description`.
- Audit metadata.

Dữ liệu seed từ migration:

- 3-4 tuổi.
- 4-5 tuổi.
- 5-6 tuổi.

### 5.6. `resources`

Bảng lõi của kho học liệu.

Trường chính:

- `id`: UUID dạng `CHAR(36)`.
- `title`: tên học liệu.
- `slug`: URL thân thiện, duy nhất.
- `description`: mô tả.
- `file_url`: URL file hoặc link YouTube.
- `thumbnail_url`: ảnh đại diện.
- `resource_type`: `FILE`, `YOUTUBE`, `EXTERNAL_LINK`.
- `file_type`: phân loại file như `VIDEO`, `DOCUMENT`, `PDF`, `EXCEL`, `POWERPOINT`, `IMAGE`.
- `file_extension`: đuôi file hoặc `youtube`.
- `file_size`: dung lượng file.
- `duration`: thời lượng video.
- `highlights`: JSON list điểm nổi bật.
- `views_count`: số lượt xem.
- `download_count`: số lượt tải.
- `average_rating`: điểm đánh giá trung bình.
- `topic_id`: FK đến `topics`.
- `status`: `PENDING`, `APPROVED`, `REJECTED`.
- `visibility`: `PUBLIC`, `PRIVATE`.
- `is_deleted`: xóa mềm.
- `rejection_reason`: lý do từ chối.
- Audit metadata.

### 5.7. `resource_age_groups`

Bảng many-to-many giữa tài nguyên và nhóm tuổi.

- `resource_id`: FK đến `resources`.
- `age_group_id`: FK đến `age_groups`.

Một tài nguyên có thể phù hợp nhiều nhóm tuổi, ví dụ video bài hát có thể dùng cho 3-4 và 4-5 tuổi.

### 5.8. `favorites`

Lưu tài nguyên yêu thích của user.

- `user_id`: FK đến `users`.
- `resource_id`: FK đến `resources`.
- `created_at`.

Khóa chính kép: `(user_id, resource_id)`.

### 5.9. `comments`

Bình luận và đánh giá tài nguyên.

- `id`.
- `content`.
- `rating`: mặc định 5.
- `user_id`.
- `resource_id`.
- `created_at`, `updated_at`.

Khi thêm/xóa bình luận, service cập nhật lại `average_rating` của resource.

### 5.10. `banners`

Banner/slide hiển thị trên portal.

Trường chính:

- `id`.
- `title`.
- `subtitle`.
- `platform`: ví dụ `WEB`.
- `image_url`.
- `bg_from`, `bg_to`: màu nền/gradient class phía frontend.
- `link`: đường dẫn khi click.
- `is_active`.
- `display_order`.
- `start_date`, `end_date`.
- `is_deleted`.
- Audit metadata.

### 5.11. `audit_log`

Lưu nhật ký thao tác hệ thống.

Trường chính:

- `id`.
- `action`: ví dụ `CREATE`, `UPDATE`, `DELETE`, `LOGIN`, `LOGOUT`, `UPLOAD`, `DOWNLOAD`, `APPROVE`, `REJECT`, `RESTORE`.
- `username`: người thực hiện.
- `target`: đối tượng tác động.
- `detail`: mô tả chi tiết.
- `ip_address`.
- `user_agent`.
- `timestamp`.

## 6. Quan hệ dữ liệu

- `users` 1-n `user_roles`.
- `categories` 1-n `topics`.
- `topics` 1-n `resources`.
- `resources` n-n `age_groups` qua `resource_age_groups`.
- `users` n-n `resources` qua `favorites`.
- `users` 1-n `comments`.
- `resources` 1-n `comments`.
- `users` được tham chiếu qua `created_by`, `updated_by` ở các bảng nghiệp vụ.

Luồng phân cấp kho dữ liệu:

`Category -> Topic -> Resource -> AgeGroup/Favorite/Comment/Stats`

Ví dụ:

`Kho tài nguyên -> Video & Clip minh họa -> Video bài hát rửa tay -> Nhóm tuổi 3-4, 4-5 -> Bình luận/đánh giá/yêu thích`

## 7. Nghiệp vụ xác thực và tài khoản

### 7.1. Đăng ký

Endpoint: `POST /api/v1/auth/register`

Luồng:

1. Kiểm tra username/email chưa tồn tại.
2. Tạo user role `USER`, trạng thái `PENDING`, `emailVerified=false`.
3. Sinh OTP xác thực email và lưu Redis.
4. Gửi email OTP sau khi transaction commit.
5. Ghi audit log `CREATE`.
6. Không trả access token, user phải verify email trước khi login.

### 7.2. Xác thực email

Endpoint: `POST /api/v1/auth/verify-email`

Luồng:

1. Verify OTP trước để hạn chế dò email.
2. Tìm user theo email.
3. Set `emailVerified=true`.
4. Nếu user đang `PENDING`, chuyển thành `ACTIVE`.
5. Xóa cache liên quan.

### 7.3. Đăng nhập

Endpoint: `POST /api/v1/auth/login`

Luồng:

1. Rate limit theo email/IP.
2. Tìm user active theo username hoặc email.
3. Kiểm tra BCrypt password.
4. Chặn login nếu user bị xóa mềm, pending, blocked, inactive hoặc chưa verify email.
5. Sinh JWT.
6. Set JWT vào cookie `accessToken` dạng HttpOnly.
7. Ghi audit log `LOGIN`.

### 7.4. Đăng xuất

Endpoint: `POST /api/v1/auth/logout`

Luồng:

1. Lấy JWT từ cookie.
2. Đưa token vào Redis blacklist đến khi token hết hạn.
3. Xóa cookie `accessToken`.
4. Ghi audit log `LOGOUT`.

### 7.5. Quên và đặt lại mật khẩu

Endpoint:

- `POST /api/v1/auth/forgot-password`.
- `POST /api/v1/auth/reset-password`.

Luồng:

1. Forgot password trả response đồng nhất, không tiết lộ email có tồn tại hay không.
2. Chỉ user `ACTIVE` mới được reset.
3. OTP lưu Redis theo purpose `PASSWORD_RESET`.
4. Reset password verify OTP trước, sau đó đổi password.
5. Tăng `tokenVersion` để JWT cũ mất hiệu lực.

### 7.6. Quản trị user

Endpoint nhóm: `/api/v1/users`

Admin có thể:

- Tạo user.
- Tìm kiếm/lọc user theo keyword, role, status.
- Cập nhật họ tên, điện thoại, bio, role, status.
- Xóa mềm user.
- Restore user.
- Block/unblock user kèm lý do.
- Khởi tạo và xác nhận reset mật khẩu cho user.

User thường có thể:

- Xem `/me`.
- Cập nhật profile.
- Đổi mật khẩu.
- Upload avatar.

## 8. Nghiệp vụ danh mục và chủ đề

### 8.1. Category

Endpoint nhóm: `/api/v1/categories`

Public:

- Xem danh sách category, có phân trang/lọc theo keyword/deleted tùy controller.

Admin:

- Tạo category với icon upload qua multipart.
- Cập nhật category bằng JSON hoặc multipart.
- Xóa mềm hoặc xóa cứng.
- Xóa hàng loạt.
- Restore một hoặc nhiều category.

Quy tắc:

- `slug` duy nhất.
- `is_active` điều khiển hiển thị.
- `is_deleted` dùng cho xóa mềm.
- Category có `topicCount` để frontend hiển thị số chủ đề.

### 8.2. Topic

Endpoint nhóm: `/api/v1/topics`

Public:

- Xem danh sách topic, lọc theo `categoryId`, `keyword`, `deleted`.

Admin:

- Tạo topic thuộc category.
- Cập nhật topic.
- Xóa mềm/xóa cứng.
- Restore topic.

Quy tắc:

- Topic phải thuộc một category.
- Topic có `resourceCount` để đếm tài nguyên chưa xóa.
- Xóa cứng category/topic có thể ảnh hưởng dữ liệu con theo ràng buộc cascade.

## 9. Nghiệp vụ kho học liệu

### 9.1. Upload tài nguyên

Endpoint: `POST /api/v1/resources`

Quyền: `ADMIN`, `TEACHER`.

Input:

- `file` hoặc `youtubeLink`.
- `thumbnail` tùy chọn.
- `title`.
- `description`.
- `topicId`.
- `ageGroupIds`.
- `duration` tùy chọn.

Luồng với file:

1. Xác thực user upload.
2. Kiểm tra topic tồn tại.
3. Upload file lên MinIO theo folder resource.
4. Xác định `fileType` từ extension.
5. Upload thumbnail nếu có.
6. Tạo resource với status mặc định `PENDING`.
7. Gán age groups nếu có.
8. Ghi audit log `CREATE/UPLOAD`.

Luồng với YouTube:

1. Trích xuất YouTube video ID.
2. Kiểm tra video có thể truy cập.
3. Lưu `fileUrl` là YouTube link.
4. Set `resourceType=YOUTUBE`, `fileExtension=youtube`.
5. Lấy duration nếu có API key/hỗ trợ.
6. Nếu không upload thumbnail riêng, dùng thumbnail YouTube `https://img.youtube.com/vi/{id}/hqdefault.jpg`.
7. Tạo resource `PENDING`.

Quy tắc thumbnail:

- Chỉ nhận ảnh `image/jpeg`, `image/png`, `image/webp`.
- Giới hạn size theo `app.resource.thumbnail-max-bytes`, mặc định 5 MB.

### 9.2. Portal xem tài nguyên

Endpoint: `GET /api/v1/resources`

Public.

Chỉ trả tài nguyên:

- `isDeleted=false`.
- `visibility=PUBLIC`.
- `status=APPROVED`.

Bộ lọc:

- `keyword`.
- `topicId`, `categoryId`, `ageGroupId`.
- `topicSlugs`, `categorySlugs`, `ageSlugs`.
- `types`.
- Phân trang `page`, `size`.

### 9.3. Xem chi tiết tài nguyên

Endpoint: `GET /api/v1/resources/{slug}`

Public.

Trả chi tiết resource theo slug, kèm:

- Topic.
- Age groups.
- Trạng thái favorite nếu user đang đăng nhập.
- Metadata tạo/sửa.

### 9.4. Tăng lượt xem

Endpoint: `PUT /api/v1/resources/{id}/view`

Public.

Luồng:

- Tăng `viewsCount`.
- Có nhận IP từ request để hỗ trợ chống đếm trùng hoặc tracking, tùy implementation repository/service.

### 9.5. Tải file

Endpoint: `GET /api/v1/resources/{id}/file`

Public theo SecurityConfig hiện tại.

Luồng:

1. Tìm resource.
2. Chặn nếu resource đã xóa.
3. Chặn nếu là YouTube vì không tải trực tiếp video YouTube.
4. Lấy stream file từ MinIO.
5. Trả `Content-Disposition: attachment`.
6. Tăng `downloadCount`.

### 9.6. Quản lý tài nguyên của tôi

Endpoint: `GET /api/v1/resources/me`

Quyền: authenticated.

Luồng:

- Trả tài nguyên do user hiện tại tạo.
- Hỗ trợ filter giống `ResourceFilterRequest`.
- Cho giáo viên xem tài nguyên pending/rejected/private của mình.

### 9.7. Cập nhật tài nguyên

Endpoint:

- `PUT /api/v1/resources/{id}` JSON.
- `PUT /api/v1/resources/{id}` multipart.

Quyền: `ADMIN`, `TEACHER`.

Luồng:

- Admin có thể cập nhật status từ form.
- Teacher/uploader không được tin payload `status`; nếu sửa nội dung tài nguyên thì hệ thống đưa về luồng chờ duyệt tùy rule hiện tại của service.
- Có thể cập nhật title, description, topic, age groups, file, YouTube link, thumbnail.
- Nếu thay file/thumbnail cũ trên MinIO, service cố gắng xóa file cũ để tránh rác.
- Nếu cập nhật YouTube link, service refresh thumbnail YouTube nếu trước đó không có thumbnail custom.

Quy tắc sở hữu:

- Admin là privileged.
- Non-admin phải là người tạo tài nguyên khi thao tác sửa/xóa/restore/visibility.
- Nếu không đúng chủ sở hữu, service ném lỗi forbidden.

### 9.8. Xóa và khôi phục tài nguyên

Endpoint:

- `DELETE /api/v1/resources/{id}?hard=false|true`.
- `DELETE /api/v1/resources/bulk?hard=false|true`.
- `PUT /api/v1/resources/{id}/restore`.
- `PATCH /api/v1/resources/bulk-restore`.

Quyền: `ADMIN`, `TEACHER`.

Luồng:

- Xóa mềm: set `isDeleted=true`.
- Xóa cứng: xóa record, xóa favorite liên quan, xóa file/thumbnail trên MinIO nếu là file nội bộ.
- Restore: set `isDeleted=false`.
- Bulk thao tác tối đa 1000 ID theo validation controller.

### 9.9. Duyệt tài nguyên

Endpoint nhóm: `/api/v1/admin/resources`

Quyền: `ADMIN`.

Admin có thể:

- Xem toàn bộ tài nguyên, gồm pending/rejected/private/deleted theo filter.
- Approve một resource.
- Reject một resource kèm lý do.
- Bulk approve.
- Bulk reject.

Luồng approve:

- Set `status=APPROVED`.
- Clear `rejectionReason`.
- Ghi audit log `APPROVE`.

Luồng reject:

- Set `status=REJECTED`.
- Lưu `rejectionReason`.
- Publish `ResourceRejectedEvent` để gửi email/thông báo cho người upload.
- Ghi audit log `REJECT`.

### 9.10. Visibility

Endpoint: `PATCH /api/v1/resources/{id}/visibility`

Quyền: `ADMIN`, `TEACHER`.

Trạng thái:

- `PUBLIC`: hiển thị trên portal nếu resource cũng được `APPROVED`.
- `PRIVATE`: không hiển thị trên portal public.

Visibility thay thế cột cũ `is_active` của resources từ migration V17.

### 9.11. Favorite

Endpoint: `POST /api/v1/resources/{id}/favorite`

Quyền: authenticated.

Luồng:

- Nếu user đã favorite resource thì xóa favorite và trả `isFavorited=false`.
- Nếu chưa favorite thì tạo favorite và trả `isFavorited=true`.

### 9.12. Bình luận và đánh giá

Endpoint nhóm: `/api/v1/comments`

Authenticated user:

- Tạo comment/rating.
- Xóa comment của mình hoặc theo rule admin/service.

Public:

- Xem comments theo resource.

Luồng:

- Comment có `content`, `rating`, user, resource.
- Khi thêm/xóa comment, cập nhật lại `averageRating` của resource.
- Ghi audit log khi thêm/xóa.

## 10. Nghiệp vụ banner

Endpoint nhóm: `/api/v1/banners`

Public:

- `GET /api/v1/banners`: lấy banner active theo `platform`.

Admin:

- `GET /api/v1/banners/all`: xem toàn bộ banner.
- `POST /api/v1/banners`: tạo banner, upload ảnh.
- `PUT /api/v1/banners/{id}`: cập nhật banner, có thể thay ảnh.
- `PATCH /api/v1/banners/reorder`: đổi thứ tự.
- `PATCH /api/v1/banners/{id}/toggle`: bật/tắt.
- `DELETE /api/v1/banners/{id}`: xóa mềm.

Quy tắc hiển thị:

- Banner active.
- Không bị xóa mềm.
- Phù hợp platform nếu có filter.
- Có thứ tự `displayOrder`.
- Có thể có `startDate/endDate` để giới hạn thời gian hiển thị.

## 11. Audit log

Endpoint nhóm: `/api/v1/audit-logs`

Quyền: `ADMIN`.

Chức năng:

- Xem log có phân trang.
- Lọc theo username, action, target, startDate, endDate.
- Export CSV qua `/api/v1/audit-logs/export`.

Nguồn log:

- Annotation `@LogAction` trong service.
- Log thủ công ở AuthService và một số luồng resource moderation.

Action đang hỗ trợ:

- `CREATE`, `UPDATE`, `DELETE`.
- `LOGIN`, `LOGOUT`.
- `VIEW`, `UPLOAD`, `DOWNLOAD`.
- `APPROVE`, `REJECT`, `RESTORE`.
- `APPROVE_BULK`, `REJECT_BULK`, `DELETE_BULK`, `RESTORE_BULK`.
- `OTHER`.

## 12. Bảo mật và phân quyền API

Public endpoints:

- Swagger/OpenAPI.
- Health/info actuator.
- `/api/v1/auth/**`.
- GET categories/topics/age-groups/banners/resources.
- PUT `/api/v1/resources/*/view`.

Authenticated endpoints:

- `/api/v1/users/me`.
- `/api/v1/users/profile`.
- `/api/v1/users/change-password`.
- `/api/v1/users/avatar`.
- Favorite resource.
- Comment create/delete.

Admin endpoints:

- Quản lý users.
- CRUD category/topic/banner.
- Admin resource moderation.
- Audit logs.

Teacher/Admin endpoints:

- Upload tài nguyên.
- Cập nhật tài nguyên.
- Xóa/restore tài nguyên.
- Cập nhật thumbnail.
- Cập nhật visibility.

Cơ chế bảo mật:

- JWT đọc từ cookie `accessToken`.
- Cookie HttpOnly.
- CSRF dùng `CookieCsrfTokenRepository`, bỏ qua một số endpoint auth và Swagger.
- CORS cấu hình từ `app.cors.allowed-origins`.
- Redis blacklist token khi logout.
- `tokenVersion` để vô hiệu hóa JWT cũ sau thay đổi nhạy cảm.
- Rate limit login theo email/IP.
- Last active filter cập nhật hoạt động gần nhất.

## 13. Lưu trữ file và MinIO

MinIO dùng cho:

- File tài nguyên.
- Thumbnail tài nguyên.
- Icon category.
- Ảnh banner.
- Avatar user.

Các folder logic theo `AppConstants` và service:

- Avatars.
- Resources.
- Thumbnails.
- Icons.
- Banners.

Quy tắc vận hành:

- Khi upload file thành công nhưng DB lưu lỗi, một số service có cơ chế xóa file mới để tránh rác.
- Khi thay avatar/thumbnail/file, service cố gắng xóa file cũ sau commit hoặc trong quá trình cập nhật.
- YouTube thumbnail không xóa khỏi MinIO vì là URL ngoài.
- Download file stream từ MinIO về client, không load toàn bộ file vào memory.

## 14. Redis usage

Redis được dùng cho:

- OTP xác thực email.
- OTP reset password.
- OTP admin reset password.
- JWT blacklist.
- Cache token version.
- Rate limit login.
- Last active sync/cache.

Scheduler:

- `RedisSyncScheduler`: đồng bộ dữ liệu active/cache về DB.
- `AuditLogCleanupScheduler`: dọn audit log cũ theo cấu hình service/scheduler.

## 15. Seed dữ liệu ban đầu

Khi app khởi động, `DataSeeder` thực hiện:

- Tạo admin nếu chưa có.
- Ở môi trường dev/local/test, tạo teacher demo `teacher_hoa`.
- Seed categories và topics nếu chưa có.
- Upload icon category lên MinIO.
- Seed banners và upload ảnh banner lên MinIO.

Admin mặc định:

- Username mặc định: `admin`.
- Email mặc định: `admin@kindergarten.com`.
- Password mặc định local: `admin123`.

Quy tắc production:

- Nếu không phải môi trường dev/local/test mà vẫn dùng password admin mặc định, app sẽ fail sớm để tránh deploy với credential yếu.

## 16. API tổng hợp

### Auth

- `POST /api/v1/auth/login`: đăng nhập.
- `POST /api/v1/auth/register`: đăng ký.
- `POST /api/v1/auth/logout`: đăng xuất.
- `POST /api/v1/auth/forgot-password`: gửi OTP quên mật khẩu.
- `POST /api/v1/auth/reset-password`: reset mật khẩu bằng OTP.
- `POST /api/v1/auth/verify-email`: xác thực email.
- `POST /api/v1/auth/resend-verification`: gửi lại OTP xác thực email.

### Users

- `GET /api/v1/users/me`: xem user hiện tại.
- `PUT /api/v1/users/profile`: cập nhật profile.
- `PUT /api/v1/users/change-password`: đổi mật khẩu.
- `POST /api/v1/users/avatar`: upload avatar.
- `POST /api/v1/users`: admin tạo user.
- `GET /api/v1/users`: admin xem/lọc user.
- `PUT /api/v1/users/{id}`: admin cập nhật user.
- `DELETE /api/v1/users/{id}`: admin xóa mềm user.
- `PUT /api/v1/users/{id}/restore`: admin restore user.
- `PUT /api/v1/users/{id}/block`: admin block/unblock user.
- `POST /api/v1/users/{id}/reset-password/init`: admin gửi OTP reset.
- `POST /api/v1/users/{id}/reset-password/confirm`: admin xác nhận reset.

### Categories

- `GET /api/v1/categories`: xem danh mục.
- `POST /api/v1/categories`: admin tạo danh mục.
- `PUT /api/v1/categories/{id}`: admin cập nhật danh mục.
- `DELETE /api/v1/categories/{id}`: admin xóa danh mục.
- `DELETE /api/v1/categories/bulk`: admin xóa hàng loạt.
- `PATCH /api/v1/categories/{id}/restore`: admin restore.
- `PATCH /api/v1/categories/bulk-restore`: admin restore hàng loạt.

### Topics

- `GET /api/v1/topics`: xem chủ đề.
- `POST /api/v1/topics`: admin tạo chủ đề.
- `PUT /api/v1/topics/{id}`: admin cập nhật chủ đề.
- `DELETE /api/v1/topics/{id}`: admin xóa chủ đề.
- `PATCH /api/v1/topics/{id}/restore`: admin restore chủ đề.

### Age Groups

- `GET /api/v1/age-groups`: xem nhóm tuổi.

### Resources

- `POST /api/v1/resources`: upload tài nguyên.
- `GET /api/v1/resources`: portal xem tài nguyên approved/public.
- `GET /api/v1/resources/me`: xem tài nguyên của tôi.
- `GET /api/v1/resources/{slug}`: xem chi tiết theo slug.
- `PUT /api/v1/resources/{id}/view`: tăng lượt xem.
- `GET /api/v1/resources/{id}/file`: tải file.
- `DELETE /api/v1/resources/{id}`: xóa tài nguyên.
- `DELETE /api/v1/resources/bulk`: xóa nhiều tài nguyên.
- `PUT /api/v1/resources/{id}/restore`: restore tài nguyên.
- `PATCH /api/v1/resources/bulk-restore`: restore nhiều tài nguyên.
- `PUT /api/v1/resources/{id}`: cập nhật tài nguyên.
- `POST /api/v1/resources/{id}/thumbnail`: cập nhật thumbnail.
- `POST /api/v1/resources/{id}/favorite`: bật/tắt yêu thích.
- `PATCH /api/v1/resources/{id}/visibility`: đổi public/private.

### Admin Resources

- `GET /api/v1/admin/resources`: admin xem toàn bộ tài nguyên.
- `PATCH /api/v1/admin/resources/{id}/approve`: duyệt tài nguyên.
- `PATCH /api/v1/admin/resources/{id}/reject`: từ chối tài nguyên.
- `PATCH /api/v1/admin/resources/bulk-approve`: duyệt hàng loạt.
- `PATCH /api/v1/admin/resources/bulk-reject`: từ chối hàng loạt.

### Comments

- `POST /api/v1/comments`: tạo bình luận/đánh giá.
- `GET /api/v1/comments`: xem bình luận.
- `DELETE /api/v1/comments/{id}`: xóa bình luận.

### Banners

- `GET /api/v1/banners`: xem banner active.
- `GET /api/v1/banners/all`: admin xem tất cả banner.
- `POST /api/v1/banners`: admin tạo banner.
- `PUT /api/v1/banners/{id}`: admin cập nhật banner.
- `PATCH /api/v1/banners/reorder`: admin đổi thứ tự banner.
- `PATCH /api/v1/banners/{id}/toggle`: admin bật/tắt banner.
- `DELETE /api/v1/banners/{id}`: admin xóa banner.

### Audit Logs

- `GET /api/v1/audit-logs`: admin xem audit log.
- `GET /api/v1/audit-logs/export`: admin export CSV.

## 17. Các nghiệp vụ liên quan kho dữ liệu trường mầm non

### 17.1. Phân loại học liệu theo chuyên môn mầm non

Hệ thống tổ chức học liệu theo danh mục lớn và chủ đề nhỏ. Đây là cấu trúc phù hợp cho trường mầm non vì giáo viên thường tìm tài liệu theo:

- Độ tuổi trẻ.
- Chủ đề giáo dục.
- Loại hoạt động.
- Tài liệu chuyên môn.
- Kênh phối hợp với phụ huynh.

Ví dụ nghiệp vụ:

- Giáo viên khối Lá tìm giáo án 5-6 tuổi.
- Giáo viên âm nhạc tìm bài hát thiếu nhi theo chủ đề.
- Ban giám hiệu tìm biểu mẫu/sổ sách chuyên môn.
- Phụ huynh xem tài liệu dinh dưỡng và tâm lý trẻ.

### 17.2. Vòng đời học liệu

Một học liệu có vòng đời:

1. Giáo viên/Admin upload.
2. Hệ thống lưu file hoặc link YouTube.
3. Resource ở trạng thái `PENDING`.
4. Admin kiểm duyệt.
5. Nếu đạt yêu cầu: `APPROVED`.
6. Nếu không đạt: `REJECTED` kèm lý do.
7. Khi `APPROVED` và `PUBLIC`, học liệu xuất hiện trên portal.
8. Người dùng xem, tải, yêu thích, bình luận, đánh giá.
9. Giáo viên/Admin có thể cập nhật.
10. Tài nguyên có thể bị xóa mềm, restore hoặc xóa cứng.

### 17.3. Kiểm duyệt nội dung

Kiểm duyệt là nghiệp vụ quan trọng vì kho học liệu dùng trong môi trường giáo dục trẻ nhỏ.

Admin cần kiểm tra:

- Nội dung phù hợp lứa tuổi.
- File không lỗi, không sai định dạng.
- Video YouTube truy cập được.
- Tài liệu đúng chủ đề/danh mục.
- Không chứa nội dung không phù hợp.
- Mô tả rõ ràng.
- Thumbnail phù hợp.

### 17.4. Tìm kiếm và lọc học liệu

Hệ thống hỗ trợ tìm kiếm theo:

- Keyword.
- Category.
- Topic.
- Age group.
- Slug category/topic/age group.
- Type.
- Status với màn admin/my resources.
- Creator với màn admin.

Điều này phục vụ các màn frontend:

- Trang kho học liệu public.
- Trang quản lý tài nguyên của giáo viên.
- Trang duyệt tài nguyên của admin.
- Trang dashboard chuyên môn.

### 17.5. Đánh giá chất lượng học liệu

User có thể comment/rating. Resource lưu `averageRating` để:

- Hiển thị chất lượng học liệu.
- Gợi ý tài liệu tốt.
- Hỗ trợ admin/giáo viên đánh giá mức độ hữu ích.

### 17.6. Theo dõi sử dụng học liệu

Resource có:

- `viewsCount`: lượt xem.
- `downloadCount`: lượt tải.
- `favorite`: lượt yêu thích qua bảng favorites.
- `comments/rating`: phản hồi chất lượng.

Các chỉ số này giúp nhà trường biết:

- Tài liệu nào được giáo viên/phụ huynh dùng nhiều.
- Chủ đề nào có nhu cầu cao.
- Học liệu nào cần cập nhật hoặc thay thế.

### 17.7. Quản trị truyền thông nội bộ qua banner

Banner phục vụ:

- Giới thiệu tài nguyên nổi bật.
- Điều hướng đến chủ đề/danh mục quan trọng.
- Truyền thông chuyên đề giáo dục.
- Thông báo hoạt động theo thời gian.

Admin kiểm soát:

- Thứ tự banner.
- Nền/màu hiển thị.
- Ảnh banner.
- Platform.
- Thời gian bắt đầu/kết thúc.
- Bật/tắt banner.

### 17.8. An toàn vận hành và truy vết

Audit log giúp truy vết:

- Ai tạo/sửa/xóa tài nguyên.
- Ai duyệt/từ chối học liệu.
- Ai đăng nhập/đăng xuất.
- Ai thao tác với user, category, topic, banner.
- IP và user agent thao tác.

Đây là phần cần thiết cho quản trị trường học vì dữ liệu có thể liên quan đến tài liệu chuyên môn, tài khoản giáo viên và kênh phụ huynh.

## 18. Quy tắc dữ liệu cần lưu ý khi phát triển tiếp

- Không dùng lại `is_active` cho resource vì đã thay bằng `visibility`.
- Public resource phải thỏa cả `status=APPROVED`, `visibility=PUBLIC`, `is_deleted=false`.
- `createdBy` là user ID kiểu Long, không phải username.
- Khi xóa mềm user, username/email bị đổi hậu tố để giải phóng unique constraint.
- Khi restore user, cần kiểm tra username/email gốc chưa bị user khác chiếm.
- Không xóa file MinIO nếu URL là thumbnail YouTube.
- Không cho download resource YouTube qua endpoint file.
- Nếu teacher sửa nội dung đã duyệt, cần giữ hoặc đưa lại về pending theo rule service hiện tại; khi thay đổi rule cần thống nhất với nghiệp vụ kiểm duyệt.
- Bulk delete/restore resource giới hạn danh sách ID từ 1 đến 1000.
- Các filter list hỗ trợ dạng lặp param hoặc comma-separated, ví dụ `types=PDF,VIDEO`.

## 19. Điểm cần kiểm tra thêm nếu bàn giao/hoàn thiện

- Chuẩn hóa encoding tiếng Việt trong một số file comments/seed đang hiển thị mojibake.
- Kiểm tra lại endpoint `GET /api/v1/resources/{id}/file` đang public theo security; nếu file chỉ dành cho user đăng nhập thì cần đổi phân quyền.
- Xác nhận rule teacher có được sửa/xóa tài nguyên của giáo viên khác không; service hiện có hàm validate ownership, cần đảm bảo mọi endpoint gọi đúng.
- Hoàn thiện nghiệp vụ `EXTERNAL_LINK` nếu frontend/backend cần dùng.
- Thêm API quản trị `age_groups` nếu nhà trường cần tự tạo nhóm tuổi ngoài seed.
- Xác nhận chính sách bucket MinIO cho file public/private tương ứng với `visibility`.
- Bổ sung báo cáo thống kê theo category/topic/age group/download/view/rating nếu cần dashboard quản trị.
