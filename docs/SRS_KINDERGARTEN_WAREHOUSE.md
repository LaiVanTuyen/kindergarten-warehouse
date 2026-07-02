# SRS - Kindergarten Digital Resource Warehouse

## 1. Tổng quan

### 1.1. Mục đích

Kindergarten Digital Resource Warehouse là hệ thống kho học liệu số cho trường mầm non. Hệ thống cho phép nhà trường quản lý tài khoản, danh mục học liệu, chủ đề, nhóm tuổi, banner truyền thông, tài nguyên học tập, bình luận/đánh giá, yêu thích, thống kê lượt xem/lượt tải và nhật ký thao tác.

Tài liệu này mô tả phạm vi nghiệp vụ, actor, chức năng, dữ liệu, yêu cầu phi chức năng và các điểm cần hoàn thiện để dự án đạt mức triển khai thực tế.

### 1.2. Phạm vi hệ thống

Backend hiện tại là REST API Spring Boot 3, Java 17, MySQL, Redis, MinIO/S3 compatible storage, Flyway migration, Spring Security JWT cookie/Bearer token, i18n message, audit log, scheduler và OpenAPI cho môi trường dev.

Hệ thống hiện tập trung vào kho học liệu số, không phải kho vật tư/vật lý. Các nghiệp vụ nhập/xuất tồn kho vật tư, định mức mua sắm, phiếu nhập kho, phiếu xuất kho, nhà cung cấp, lô/hạn dùng chưa nằm trong scope hiện tại.

## 2. Actor và quyền

### 2.1. Actor

- Guest: xem danh mục, chủ đề, nhóm tuổi, banner, tài nguyên public approved, bình luận public.
- User/Phụ huynh: đăng ký, xác thực email, đăng nhập, cập nhật hồ sơ, đổi mật khẩu, yêu thích tài nguyên, bình luận/đánh giá tài nguyên public.
- Teacher/Giáo viên: đăng nhập, quản lý hồ sơ, upload tài nguyên, xem tài nguyên của mình, cập nhật/xóa/khôi phục tài nguyên của mình, gửi lại tài nguyên để duyệt.
- Admin: quản trị người dùng, danh mục, chủ đề, banner, tài nguyên, duyệt/từ chối tài nguyên, audit log, export audit log.

### 2.2. Ma trận quyền cấp cao

| Chức năng | Guest | User | Teacher | Admin |
| --- | --- | --- | --- | --- |
| Xem portal public | Có | Có | Có | Có |
| Đăng ký/xác thực email | Có | Có | Có | Có |
| Cập nhật profile/avatar | Không | Có | Có | Có |
| Upload tài nguyên | Không | Không | Có | Có |
| Duyệt/từ chối tài nguyên | Không | Không | Không | Có |
| Quản lý category/topic/banner | Không | Không | Không | Có |
| Quản lý user | Không | Không | Không | Có |
| Audit log/export | Không | Không | Không | Có |

## 3. Nghiệp vụ chính

### 3.1. Xác thực và tài khoản

- Đăng ký user mới với trạng thái `PENDING`, yêu cầu OTP email trước khi đăng nhập.
- Đăng nhập bằng username/email và password.
- JWT được lưu qua HttpOnly cookie `accessToken`, đồng thời hỗ trợ Bearer token.
- Giới hạn đăng nhập sai theo cặp identifier + IP trong Redis.
- Logout đưa token vào blacklist Redis đến khi token hết hạn.
- Đổi/reset mật khẩu tăng `tokenVersion` để vô hiệu hóa JWT cũ.
- Admin có thể tạo user trực tiếp ở trạng thái trusted, active và email verified.
- Admin có thể block/unblock, soft-delete/restore user.
- Có guard chống admin tự xóa/khóa/hạ quyền chính mình và chống mất admin active cuối cùng.

### 3.2. Danh mục, chủ đề, nhóm tuổi

- Category là nhóm nội dung cấp cao, có slug, icon, mô tả, trạng thái active và soft-delete.
- Topic thuộc category, có slug, mô tả, trạng thái active và soft-delete.
- Age group mô tả nhóm tuổi mầm non, ví dụ 3-4, 4-5, 5-6 tuổi.
- Portal chỉ hiển thị tài nguyên thuộc topic/category chưa xóa và đang active.

### 3.3. Tài nguyên học liệu

- Teacher/Admin upload tài nguyên dạng file hoặc YouTube link.
- Mỗi tài nguyên có title, slug, mô tả, topic, age groups, file metadata, thumbnail, duration, visibility, status, rating trung bình, view/download count.
- File upload lưu trong MinIO ở vùng private; client tải file qua API có kiểm tra quyền.
- Thumbnail/avatar/banner/category icon là asset public theo bucket policy.
- Admin upload tài nguyên thì tự động `APPROVED`; Teacher upload thì `PENDING`.
- Admin duyệt/từ chối tài nguyên. Khi từ chối, hệ thống gửi email lý do cho uploader.
- Teacher sửa tài nguyên thì resource tự động quay lại `PENDING` để duyệt lại.
- Hỗ trợ soft-delete, hard-delete, restore và bulk approve/reject/delete/restore.
- Lượt xem/lượt tải được ghi đệm trong Redis rồi scheduler đồng bộ về DB để giảm write pressure.

### 3.4. Bình luận, đánh giá và yêu thích

- User đã đăng nhập có thể bình luận và đánh giá 1-5 sao với tài nguyên public approved.
- Guest có thể xem bình luận của tài nguyên public approved.
- Khi thêm/xóa bình luận, hệ thống cập nhật rating trung bình của tài nguyên.
- User có thể favorite/unfavorite tài nguyên public approved.

### 3.5. Banner và portal content

- Admin quản lý banner theo platform, thứ tự hiển thị, thời gian bắt đầu/kết thúc, trạng thái active và soft-delete.
- Guest có thể xem banner active/public.

### 3.6. Audit log

- Ghi nhật ký các hành động quan trọng: login/logout, CRUD user/category/topic/banner/resource, approve/reject, block/unblock, upload avatar/thumbnail.
- Audit log lưu username, action, target, detail, timestamp, IP, user-agent.
- Admin có thể lọc audit log và export CSV theo snapshot thời gian để tránh pagination drift.
- Có scheduler dọn audit log cũ theo batch.

## 4. Yêu cầu phi chức năng

### 4.1. Security

- Spring Security + method-level authorization.
- JWT cookie HttpOnly; production bật secure cookie và SameSite=None.
- CSRF được bật, bỏ qua một số endpoint auth public.
- Password dùng BCrypt.
- Token blacklist và token version hỗ trợ revoke token.
- Email reset/verify dùng OTP Redis với giới hạn attempt.
- CORS lấy từ env `CORS_ALLOWED_ORIGINS`.
- File tài nguyên không public trực tiếp; API download kiểm tra visibility/status trước khi trả stream.

### 4.2. Performance và limit

- Pagination được giới hạn page size tối đa 100 qua `PageableUtils` cho các list chính.
- Bulk input có giới hạn 1000 item ở controller.
- Category bulk xử lý chunk 100 item để tránh query quá lớn.
- Multipart upload mặc định 50 MB/request.
- Thumbnail giới hạn qua `RESOURCE_THUMBNAIL_MAX_BYTES`, mặc định 5 MB.
- Avatar giới hạn qua `AppConstants.AVATAR_MAX_BYTES`.
- View count có rate limit 60 giây theo IP + resource.
- Redis batch sync giảm số lần update DB cho view/download count.
- Migration mới bổ sung index cho các query nóng: portal resource list, owner resource list, resource-topic lookup, comments, favorites, audit timestamp.

### 4.3. Reliability và vận hành

- Flyway quản lý schema migration; production dùng `ddl-auto: validate`.
- Actuator expose health/info.
- Rollbar hỗ trợ error tracking khi enabled.
- Docker/Docker Compose có cấu hình dev/prod.
- MinIO init tự kiểm tra/tạo bucket và apply bucket policy.
- Email/audit chạy async để giảm độ trễ request chính.

## 5. Mô hình dữ liệu cấp cao

- `users`: tài khoản, trạng thái, email verification, token version, optimistic lock version.
- `user_roles`: role ADMIN/TEACHER/USER.
- `categories`: danh mục cấp cao.
- `topics`: chủ đề thuộc category.
- `age_groups`: nhóm tuổi.
- `resources`: tài nguyên học liệu.
- `resource_age_groups`: quan hệ nhiều-nhiều resource-age group.
- `favorites`: tài nguyên yêu thích theo user.
- `comments`: bình luận/rating.
- `banners`: banner portal.
- `audit_log`: nhật ký hệ thống.

## 6. API module cấp cao

- `/api/v1/auth`: login, register, logout, forgot/reset password, verify/resend email.
- `/api/v1/users`: profile, avatar, admin user management, block, restore, reset password.
- `/api/v1/categories`: list/create/update/delete/restore/bulk.
- `/api/v1/topics`: list/create/update/delete/restore.
- `/api/v1/age-groups`: list nhóm tuổi.
- `/api/v1/resources`: portal list, detail, upload, update, delete, restore, favorite, view count, download file, thumbnail, visibility.
- `/api/v1/admin/resources`: admin list, approve/reject/bulk approve/bulk reject.
- `/api/v1/comments`: create/list/delete comment.
- `/api/v1/banners`: list/admin list/create/update/reorder/toggle/delete.
- `/api/v1/audit-logs`: admin filter/export audit log.

## 7. Đánh giá mức đáp ứng dự án thực tế

### 7.1. Điểm đã đáp ứng tốt

- Có phân quyền rõ theo Guest/User/Teacher/Admin.
- Có workflow duyệt tài nguyên phù hợp môi trường trường học.
- Có soft-delete/restore cho nhiều entity chính.
- Có audit log, i18n, validation, exception handler tập trung.
- Có quản lý file riêng qua MinIO thay vì lưu file trong DB.
- Có Redis cho rate limit, OTP, token revoke, last-active và stat buffering.
- Có Flyway migration và production profile tách biệt.
- Có unit test cho Auth/User/Resource và bổ sung test cho Comment rule.

### 7.2. Khoảng cách còn lại trước production lớn

- Chưa có integration test chạy với MySQL/Redis/MinIO thật qua Testcontainers.
- Chưa có antivirus/malware scan cho file upload.
- File type hiện chủ yếu dựa trên extension/content-type; nên kiểm tra magic bytes cho file quan trọng.
- Chưa có refresh token/rotation, session management nâng cao hoặc logout all devices endpoint riêng.
- Chưa có rate limit cho register/forgot-password/resend-verification/comment/favorite ngoài login/view/OTP.
- Chưa có moderation workflow cho comment/rating.
- Chưa có dashboard SLA/metrics chi tiết, tracing, structured logs.
- Chưa có backup/restore runbook cho MySQL, Redis, MinIO.
- Chưa có contract test/API regression test cho toàn bộ endpoint.
- Nếu mục tiêu là kho vật tư/vật lý của trường, cần bổ sung module inventory riêng: item, stock, warehouse location, supplier, purchase/receive, issue/return, stock adjustment, min/max threshold, expiry batch, approval phiếu, báo cáo tồn kho.

## 8. Tiêu chí nghiệm thu đề xuất

- User đăng ký, nhận OTP, verify email và đăng nhập thành công.
- User chưa verify/blocked/deleted không đăng nhập được.
- Teacher upload resource file/YouTube tạo trạng thái pending.
- Admin approve resource thì guest xem được trên portal.
- Admin reject resource thì teacher nhận email lý do.
- Teacher sửa resource đã approved thì resource quay về pending.
- Guest không xem/tải/comment/favorite resource private/pending/rejected/deleted.
- Download file trả đúng filename, content type và chỉ qua API.
- Pagination size vượt 100 bị giới hạn an toàn.
- View/download count tăng qua Redis và đồng bộ DB bởi scheduler.
- Audit log ghi đủ action chính và export CSV được.
- `mvn clean test` pass trước khi merge/deploy.

## 9. Kết luận

Backend hiện đã đủ nền tảng cho một MVP/phiên bản pilot thực tế của kho học liệu số mầm non: có xác thực, phân quyền, quản trị học liệu, duyệt nội dung, lưu trữ file, thống kê, audit và cấu hình production cơ bản.

Để đạt chuẩn production nghiêm túc hơn, ưu tiên tiếp theo nên là integration test với hạ tầng thật, hardening upload file, rate limit các endpoint public nhạy cảm, backup/observability runbook và bổ sung module inventory nếu nghiệp vụ cuối cùng là quản lý kho vật tư thay vì kho học liệu số.
