# Design Review — Kindergarten Warehouse Backend

> Phạm vi: Kiến trúc backend & code · Database/Schema · API/REST · Bảo mật.
> Mục tiêu: Phân tích thiết kế hiện tại, chỉ ra vấn đề + rủi ro + đề xuất sửa. **Tài liệu phân tích, chưa đụng code.**
> Ngày: 2026-06-14 · Nhánh: `fix/senior-review-phase1`

---

## 0. Tóm tắt điều hành (Executive Summary)

Hệ thống đã có nền tảng tốt: phân lớp controller/service/repository rõ, bảo mật tài khoản được đầu tư (tokenVersion, blacklist, chống enumeration, optimistic lock cho user, rate-limit OTP bằng Lua script atomic), pattern `afterCommit` để tách side-effect khỏi transaction (ở `UserService`/`AuthService`), write-behind đếm view/download qua Redis.

Tuy nhiên đang tích lũy **nợ thiết kế** ở 4 nhóm chính:

| # | Vấn đề trọng yếu | Tầng | Mức độ |
|---|---|---|---|
| A | `ResourceServiceImpl` là **god service** (1.152 dòng, ~20+ method, ôm upload + filter + duyệt + favorite + stats + IO) | Kiến trúc | **Cao** |
| B | **IO MinIO bên trong `@Transactional`** → rollback DB để lại file rác trên MinIO | Kiến trúc | **Cao** |
| C | **Email reject dùng `@EventListener` (không transactional)** → rollback vẫn gửi email | Kiến trúc | **Cao** |
| D | **Migration `is_active → visibility` chỉ làm dở cho `resources`** → Category/Topic/Banner còn `is_active`, schema lệch | DB | **Cao** |
| E | **Soft-delete xung đột UNIQUE constraint** (username/email/slug) — đã xử lý cho user bằng đổi hậu tố, nhưng chưa nhất quán toàn hệ | DB | **Cao** |
| F | **Secret bị commit** (`.env` chứa `admin123` + JWT secret) và **JWT secret yếu** | Bảo mật | **Cao** |
| G | **Không nhất quán HTTP method / response format / validation** giữa các controller | API | Trung |
| H | **Thiếu test** cho phần lớn service (chỉ 3/nhiều service có test) | Kiến trúc | Cao |

**Đánh giá tổng thể:** Chạy được và tương đối an toàn ở mức cơ bản, nhưng nợ kiến trúc + schema sẽ tăng nhanh chi phí bảo trì. Khuyến nghị xử lý nhóm P0 (B, C, F) trước khi mở rộng tính năng.

### Đính chính so với khảo sát thô (đã verify từ code)

- ❌ **Không có IDOR cho download file private/pending.** `getResourceFileInfo()` ([ResourceServiceImpl.java:860-864](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceServiceImpl.java#L860-L864)) chặn **mọi** resource không phải `PUBLIC + APPROVED + chưa xóa` cho **tất cả** caller. Vấn đề thật: endpoint public + không rate-limit, và **chính chủ/admin lại không tải được file của mình khi chưa duyệt** (xem [API-3] và [SEC-4]).
- ⚠️ `validateResourceOwnership` ([dòng 782-789](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceServiceImpl.java#L782-L789)) có check `isAdmin` **lặp 2 lần** (dòng 783 và 785) — dư thừa chứ không phải lỗi logic. Mức Thấp.

### Quy ước mức độ

- **Cao**: rủi ro đúng-sai dữ liệu / bảo mật / mất nhất quán nghiêm trọng → ưu tiên sửa.
- **Trung**: nợ thiết kế, ảnh hưởng bảo trì/hiệu năng/UX.
- **Thấp**: dọn dẹp, nhất quán phong cách.

---

## 1. Kiến trúc Backend & Code

### [ARC-1] `ResourceServiceImpl` — God Service · **Cao**
- **Vị trí:** [ResourceServiceImpl.java](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceServiceImpl.java) — **1.152 dòng**, ~20+ public method.
- **Vấn đề:** Ôm quá nhiều trách nhiệm: upload (file + YouTube), filter/pagination (3 biến thể portal/admin/me), duyệt (approve/reject + bulk), favorite, đếm view/download, thumbnail, ownership, mapping. `UserService` cũng lớn (551 dòng).
- **Rủi ro:** Vi phạm SRP; mọi thay đổi có nguy cơ ảnh hưởng chéo; rất khó test.
- **Đề xuất:** Tách theo use-case: `ResourceUploadService`, `ResourceQueryService`, `ResourceModerationService` (approve/reject/bulk), `FavoriteService`, giữ `ResourceStatService` (đã có). Đưa logic filter `createBaseSpecification` thành class `ResourceSpecifications` dùng chung.

### [ARC-2] IO MinIO nằm trong transaction → rác file khi rollback · **Cao**
- **Vị trí:** `uploadResource` ([~62-178](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceServiceImpl.java#L62)), `updateResource` (~445-612), `updateThumbnail` (~648-686) — đều `@Transactional` và gọi `minioStorageService.uploadFile(...)` bên trong.
- **Vấn đề:** Upload MinIO thành công nhưng commit DB lỗi → transaction rollback, **file đã nằm trên MinIO không được xóa**.
- **Rủi ro:** Rác storage tích lũy; URL file mồ côi.
- **Đề xuất:** Tách IO ra ngoài boundary transaction; nếu DB lỗi thì `runAfterCompletion(rollback)` xóa file vừa upload (compensating action). `ResourceTxHelper` (xem [ARC-7]) dường như được tạo cho mục đích này — hoàn thiện và dùng nó.

### [ARC-3] Email reject không gắn với transaction commit · **Cao**
- **Vị trí:** Publish event tại `rejectResource` (~dòng 993) và `bulkReject` (~1139); listener [NotificationListener.java:26-27](../src/main/java/com/kindergarten/warehouse/listener/NotificationListener.java#L26-L27) dùng `@Async @EventListener`.
- **Vấn đề:** `@EventListener` chạy ngay khi `publishEvent` được gọi (trước commit). Nếu transaction rollback sau đó (vd lỗi khi ghi audit, optimistic lock, commit fail) → **email "đã từ chối" vẫn gửi cho giáo viên** dù resource không hề bị reject.
- **Đề xuất:** Đổi sang `@TransactionalEventListener(phase = AFTER_COMMIT)` (giữ `@Async`). Đây là pattern đã dùng đúng ở `UserService`/`AuthService` qua `runAfterCommit`.

### [ARC-4] Cập nhật `averageRating` có race condition · **Cao** (đúng-sai dữ liệu)
- **Vị trí:** `CommentServiceImpl.updateResourceRating` (~85-89): đọc AVG rồi `resourceRepository.save(resource)`.
- **Vấn đề:** Hai comment đồng thời → cả hai đọc AVG cũ, ghi đè nhau (lost update). `Resource` **không có** `@Version` (xem [DB-6]).
- **Đề xuất:** Dùng **UPDATE nguyên tử**: `UPDATE resources SET average_rating = (SELECT AVG(rating)...) WHERE id = ?` (1 câu JPQL/native), thay vì read-modify-write. Hoặc thêm `@Version` cho `Resource`.

### [ARC-5] Nuốt exception khi xóa file & ghi audit · **Trung**
- **Vị trí:** `deleteResourceFiles` (~791-807), nhánh delete trong `updateResource`/`updateThumbnail`; `manuallyLogAudit` (~1003-1016) bắt `Exception` rồi chỉ `log`.
- **Vấn đề:** Lỗi xóa file/ghi audit bị che; không có cơ chế dọn rác bù hay cảnh báo. Với audit, làm hổng tính toàn vẹn nhật ký.
- **Đề xuất:** Với file: gom vào hàng đợi "cần dọn" + scheduler reconcile. Với audit: ít nhất phát metric/alert, cân nhắc retry.

### [ARC-6] Service phụ thuộc `SecurityContextHolder` tĩnh · **Trung** (testability)
- **Vị trí:** `getResourceBySlug`/`getPortalResources`... (~281-284, 313-316, 727-735) đọc `SecurityContextHolder.getContext()`.
- **Vấn đề:** Trộn tầng web vào service; khó unit test (phải dựng SecurityContext).
- **Đề xuất:** Truyền `username`/`userId` (hoặc `Optional<Long>`) từ controller xuống như các method khác đã làm.

### [ARC-7] `ResourceTxHelper` là dead code · **Trung**
- **Vị trí:** [ResourceTxHelper.java](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceTxHelper.java) tồn tại (helper thu hẹp transaction) nhưng `ResourceServiceImpl` không dùng.
- **Đề xuất:** Hoặc dùng nó để giải quyết [ARC-2], hoặc xóa để tránh mơ hồ kiến trúc.

### [ARC-8] Trùng lặp logic · **Trung**
- **isAdmin/isPrivileged**: lặp ở `ResourceServiceImpl` (766-772), `UserService`, ... → gom về `User` entity hoặc `RoleChecker`.
- **FileType mapping**: 3 switch riêng — `determineFileType` (810-827), `getContentType` (892-905), `getFileExtensionByType` (908-921). Thiếu **PowerPoint (.pptx)** dù doc/seed nhắc tới → gom về 1 enum/registry duy nhất.
- **YouTube thumbnail URL** hard-code 4 nơi (145, 532, 557, 672) → đưa vào `AppConstants`.

### [ARC-9] Async pool & `CallerRunsPolicy` · **Trung**
- **Vị trí:** [AsyncConfig.java](../src/main/java/com/kindergarten/warehouse/config/AsyncConfig.java) — `emailExecutor` queue=100, `CallerRunsPolicy`.
- **Vấn đề:** Queue đầy → task chạy trên **thread HTTP request**, làm chậm response.
- **Đề xuất:** Cân nhắc reject + log/metric thay vì block request; hoặc tăng queue + giám sát.

### [ARC-10] Thiếu test · **Cao**
- **Vị trí:** Chỉ có `AuthServiceTest`, `ResourceServiceTest`, `UserServiceTest`. Thiếu test cho Comment/Banner/Category/Topic và phần lớn nhánh của `ResourceServiceImpl`.
- **Đề xuất:** Sau khi tách [ARC-1], viết unit test cho từng service nhỏ; thêm test cho luồng duyệt, ownership, race rating.

---

## 2. Database & Schema

### [DB-1] Migration `is_active → visibility` làm dở · **Cao**
- **Vị trí:** `V12` thêm `is_active` cho category/topic; `V17` chỉ thay `is_active`→`visibility` cho **resources**. `Category`/`Topic`/`Banner` vẫn dùng `is_active` boolean.
- **Vấn đề:** Schema lệch chuẩn — 1 bảng dùng enum `Visibility`, 3 bảng dùng boolean. Query phải nhớ quy ước khác nhau.
- **Đề xuất:** Quyết định 1 chuẩn. Nếu giữ `visibility` làm chuẩn → migrate nốt; nếu category/topic chỉ cần bật/tắt thì giữ `is_active` và ghi rõ "visibility chỉ áp cho resource". Tài liệu hóa rõ ràng.

### [DB-2] Soft-delete vs UNIQUE constraint · **Cao**
- **Vị trí:** `users(username,email)`, `categories(slug)`, `topics(slug)`, `resources(slug)` UNIQUE; tất cả có `is_deleted`.
- **Hiện trạng:** User đã được xử lý bằng đổi hậu tố `_deleted_{ts}` + lưu `original_*`. Nhưng category/topic/resource dựa vào slug-có-timestamp nên ít va chạm hơn — cần xác nhận đồng nhất.
- **Rủi ro:** Không thể tái dùng giá trị của bản ghi đã xóa mềm nếu chỉ dựa UNIQUE thuần.
- **Đề xuất:** Dùng **partial/filtered unique** theo `is_deleted=false` (MySQL 8: dùng generated column + unique index, hoặc giữ chiến lược đổi hậu tố nhất quán). Tài liệu hóa chiến lược restore (kiểm tra slug/username gốc chưa bị chiếm).

### [DB-3] N+1 ở Comment/Favorite · **Trung**
- **Vị trí:** `CommentRepository.findByResourceId(...)` không có `@EntityGraph`/`JOIN FETCH`; `Comment` có `@ManyToOne(LAZY)` tới User & Resource. `ResourceRepository` thì đã làm đúng (có EntityGraph).
- **Rủi ro:** Trang 16 comment → ~33 query.
- **Đề xuất:** Thêm `@EntityGraph` cho query list comment; chỉ fetch field cần (cân nhắc projection/DTO).

### [DB-4] Cột chết / mapping thiếu · **Trung/Thấp**
- `favorites.created_by` (V6) và `comments.created_by/updated_by` (V6) **có trong DB nhưng entity không map** → cột chết, audit "ai tạo" không dùng được. Chuẩn hóa: hoặc map (extend `BaseEntity`), hoặc bỏ cột.
- Category/Topic giữ **cả** `is_deleted` + `is_active` → ngữ nghĩa chồng (liên quan [DB-1]).

### [DB-5] Index khai báo ở JPA nhưng không có trong migration · **Trung**
- **Vị trí:** `User.java` `@Index` (fullname, phone, status) không xuất hiện trong `V1`/migration tương ứng; `idx_resource_active` (theo `is_active` cũ) có thể đã lỗi thời sau V17.
- **Rủi ro:** Index không được version-control → môi trường lệch nhau; index "mồ côi" theo cột đã bỏ.
- **Đề xuất:** Mọi index phải khai trong migration Flyway; rà lại index theo cột query thực tế: `resources(status, visibility, is_deleted, topic_id, slug)`, `audit_log(action, timestamp)` (composite).

### [DB-6] Optimistic lock chỉ có ở User · **Trung**
- **Vị trí:** `@Version` mới thêm cho `User` (V20). `Resource`/`Category`/`Topic`/`Banner` không có.
- **Rủi ro:** Lost update khi 2 admin sửa đồng thời; liên quan trực tiếp race [ARC-4].
- **Đề xuất:** Thêm `@Version` cho `Resource` (ưu tiên) và các entity hay sửa đồng thời.

### [DB-7] `duration` lưu chuỗi `VARCHAR(20)` · **Thấp**
- **Vị trí:** `V16`, `Resource.duration` kiểu String ("05:30").
- **Rủi ro:** Không thể `ORDER BY`/lọc theo độ dài ở DB.
- **Đề xuất:** Lưu `INT seconds`, format hiển thị ở client.

### [DB-8] ID kiểu hỗn hợp (UUID CHAR(36) vs BIGINT) · **Thấp→Trung**
- **Vị trí:** `resources.id = CHAR(36)`, các bảng khác `BIGINT`. Bảng nối (`resource_age_groups`, `favorites`) phải lưu CHAR(36).
- **Đánh giá:** Không sai, nhưng tốn storage + join chậm hơn. Nếu giữ UUID, cân nhắc lưu nhị phân (`BINARY(16)`) để tối ưu. Đây là quyết định kiến trúc, ghi nhận để cân nhắc, không bắt buộc đổi.

---

## 3. API / REST Design

### [API-1] HTTP method không nhất quán · **Cao** (semantics)
- **restore**: `users`/`resources` dùng **PUT** ([UserController](../src/main/java/com/kindergarten/warehouse/controller/UserController.java), [ResourceController.java:159](../src/main/java/com/kindergarten/warehouse/controller/ResourceController.java#L159)) nhưng `categories`/`topics` dùng **PATCH**. → thống nhất 1 kiểu (đề xuất PATCH cho thay đổi trạng thái).
- **toggle**: `resources/{id}/favorite` dùng POST còn `banners/{id}/toggle` dùng PUT → thống nhất POST/PATCH.
- **view**: `PUT /resources/{id}/view` cho thao tác **tăng đếm** (không idempotent) — PUT sai ngữ nghĩa → dùng POST.
- **bulk delete**: `DELETE` kèm `@RequestBody` (một số proxy chặn body trên DELETE) trong khi bulk-restore dùng PATCH → cân nhắc POST `/bulk-delete` cho nhất quán.

### [API-2] Định dạng request/response không đồng nhất · **Cao**
- `CommentController` tạo comment bằng **`@RequestParam`** (query string) thay vì `@RequestBody` DTO → khó mở rộng, lỗi URL-encoding với nội dung dài.
- `AuditLogController` trả **entity `Page<AuditLog>` trực tiếp** thay vì DTO → lộ field nội bộ, vỡ contract khi thêm cột.
- `downloadResource` trả `ResponseEntity<?>` — success là stream thô, lỗi lại bọc `ApiResponse` → client phải xử lý 2 dạng.
- Một số message hard-code ("Bulk approve processed" — `AdminResourceController`) thay vì qua `messageService` → vỡ i18n.

### [API-3] Validation thiếu · **Trung/Cao**
- `updateProfile` thiếu `@Valid` → ràng buộc `@Size` không chạy.
- Bulk dùng inline `@Size(min=1,max=1000)` trên `List` ở nhiều nơi (Category, Resource) thay vì DTO dùng lại → gom về `BulkRequest` DTO (resource đã có `BulkResourceRequest`, nên dùng nhất quán).
- `BulkResourceRequest.reason` là **một** chuỗi cho bulk reject → mọi resource cùng lý do (chấp nhận được nhưng nên ghi rõ trong contract).

### [API-4] Phân trang rời rạc + giới hạn không công bố · **Trung**
- Nhiều controller nhận `page/size/sortBy/sortDir` qua `@RequestParam` rời, lặp code; max size=100 (`PageableUtils`) không được tài liệu hóa.
- `CategoryController` có **workaround cho bug FE** (FE gửi `desc` vào `sortBy`) — nên sửa FE, không vá ngầm ở BE.
- **Đề xuất:** Dùng `Pageable`/`@PageableDefault`; whitelist `sortBy`; công bố giới hạn size trong OpenAPI.

### [API-5] Thiếu annotation OpenAPI · **Thấp**
- Controller hầu như không có `@Operation`/`@Tag`/`@ApiResponse` dù đã có springdoc → Swagger sơ sài. Bổ sung để self-document.

### [API-6] `ApiResponse.code` luôn `1000` khi success · **Thấp**
- Dư thừa (HTTP status đã đủ) nhưng không gây lỗi. Giữ nguyên hoặc dọn khi tiện.

---

## 4. Bảo mật (Security Design)

### [SEC-1] Secret bị commit + JWT secret yếu · **Cao**
- **Vị trí:** `.env` chứa `APP_ADMIN_PASSWORD=admin123` và `JWT_SECRET=local-dev-...`.
- **Rủi ro:** Nếu repo bị lộ → lộ secret; JWT secret độ entropy thấp (chuỗi đọc được) dễ tấn công với HS256.
- **Đề xuất:** Xóa `.env` khỏi git (và lịch sử nếu từng push), đảm bảo `.gitignore` chặn `.env`; sinh JWT secret ≥256-bit ngẫu nhiên (`openssl rand -base64 48`); production bắt buộc đổi mật khẩu admin (đã có fail-fast khi còn default — xác nhận đang bật).

### [SEC-2] CSRF cookie không HttpOnly · **Cao** (theo mô hình đe dọa)
- **Vị trí:** [SecurityConfig.java:75](../src/main/java/com/kindergarten/warehouse/config/SecurityConfig.java#L75) `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
- **Đánh giá:** Đây là pattern double-submit tiêu chuẩn (JS cần đọc token) — chấp nhận được *nếu* không có XSS. Rủi ro tăng nếu có lỗ XSS. **Phòng thủ chính là chống XSS** + cân nhắc header-based token.
- **Đề xuất:** Giữ nhưng siết CSP/escape output; hoặc chuyển token qua response header để cookie có thể HttpOnly.

### [SEC-3] CORS `allowCredentials=true` + origin từ env · **Trung**
- **Vị trí:** [SecurityConfig.java:167-180](../src/main/java/com/kindergarten/warehouse/config/SecurityConfig.java#L167-L180).
- **Rủi ro:** Nếu cấu hình origin lỏng (wildcard/subdomain) + credentials → rò cookie phiên.
- **Đề xuất:** Validate origin lúc startup (cấm `*`), cảnh báo nếu origin non-HTTPS ở profile prod.

### [SEC-4] Endpoint download public, không rate-limit · **Trung** (đã đính chính, KHÔNG phải IDOR)
- **Vị trí:** `GET /api/v1/resources/**` permitAll → gồm `/{id}/file`.
- **Thực tế:** Chỉ phục vụ `PUBLIC+APPROVED+chưa xóa` (chặn đúng). **Hai vấn đề thật:**
  1. **Chính chủ/admin không tải được file PENDING/PRIVATE của mình** (gap chức năng) — cân nhắc nhánh: nếu non-public thì cho phép owner/admin tải.
  2. **Không rate-limit download** → có thể bị lạm dụng băng thông. Thêm rate-limit theo IP.

### [SEC-5] Brute-force OTP & policy mật khẩu yếu · **Trung**
- **OTP:** TTL 5 phút, max 5 lần thử, khóa 15 phút, chỉ rate theo email (`RedisOtpService`). Thiếu backoff lũy tiến + rate theo IP. → Giảm số lần thử, thêm backoff/CAPTCHA, rate theo IP.
- **Mật khẩu:** register min **6** ký tự nhưng change-password min **8** → không nhất quán, đều yếu. → Thống nhất ≥ 8–12 + yêu cầu độ phức tạp; cấm chứa username/email.

### [SEC-6] Enumeration qua timing · **Trung/Thấp**
- **Vị trí:** `forgot-password`/`resend-verification` trả response đồng nhất (tốt) nhưng nhánh "email tồn tại" làm thêm việc → chênh timing đo được.
- **Đề xuất:** Chuẩn hóa thời gian xử lý (làm việc tối thiểu giống nhau, hoặc luôn enqueue async), log để phát hiện dò.

### [SEC-7] Upload: validate & SSRF/path-traversal · **Thấp→Trung**
- File MinIO đặt tên bằng UUID (an toàn traversal cho key sinh ra), nhưng nên validate `originalFilename` không chứa `..`/`/` trước khi dùng cho metadata/extension.
- `youtubeLink` được gọi kiểm tra accessibility → cân nhắc **SSRF**: chỉ cho host youtube hợp lệ, không follow redirect tùy ý.
- Bucket: `avatars/*`, `resources/thumbnails/*` public; `resources/files/*` private (đúng). Xác nhận policy khớp với `visibility` mong muốn.

### Điểm mạnh bảo mật (ghi nhận)
JWT + tokenVersion revocation; OTP verify bằng Lua atomic; rate-limit login theo (email+IP); ownership check tập trung ở service; audit log + masking; OTP-first chống enumeration; soft-delete user + tăng tokenVersion; không log password.

---

## 5. Lộ trình ưu tiên đề xuất

### P0 — Đúng-sai / Bảo mật (làm ngay)
1. **[ARC-3]** Đổi `@EventListener` → `@TransactionalEventListener(AFTER_COMMIT)` cho email reject.
2. **[ARC-2]** Đưa IO MinIO ra ngoài transaction + compensating delete khi rollback.
3. **[ARC-4]/[DB-6]** Cập nhật `averageRating` nguyên tử / thêm `@Version` cho `Resource`.
4. **[SEC-1]** Gỡ secret khỏi repo, sinh JWT secret mạnh, ép đổi mật khẩu admin ở prod.

### P1 — Nhất quán & nợ schema
5. **[DB-1]/[DB-4]** Quyết & hoàn tất chuẩn `is_active`/`visibility`; dọn cột chết.
6. **[DB-2]** Chuẩn hóa chiến lược soft-delete vs unique (filtered index hoặc đổi hậu tố nhất quán).
7. **[API-1]/[API-2]/[API-3]** Thống nhất HTTP method, response DTO (AuditLog), `@RequestBody` cho comment, thêm `@Valid`.
8. **[SEC-4]/[SEC-5]** Rate-limit download + cho owner/admin tải file non-public; siết policy mật khẩu/OTP.

### P2 — Cấu trúc & chất lượng
9. **[ARC-1]** Tách `ResourceServiceImpl` thành các service theo use-case; gom `ResourceSpecifications`.
10. **[ARC-8]** Gom logic trùng (role check, FileType registry, hằng số YouTube); bổ sung `.pptx`.
11. **[ARC-10]** Bổ sung test cho service còn thiếu + các luồng rủi ro.
12. **[DB-3]/[DB-5]** Thêm `@EntityGraph` cho comment; đưa mọi index vào migration.

### P3 — Dọn dẹp
13. **[ARC-7]** Dùng hoặc xóa `ResourceTxHelper`; **[API-5]** annotation OpenAPI; **[DB-7]** `duration` dạng số; **[ARC-6]** bỏ `SecurityContextHolder` trong service; dọn double-`isAdmin`.

---

## 6. Phụ lục — Bằng chứng đã verify trực tiếp

| Claim | Trạng thái | Bằng chứng |
|---|---|---|
| `ResourceServiceImpl` 1.152 dòng | ✅ Xác nhận | `wc -l` |
| Email reject dùng `@EventListener` (không transactional) | ✅ Xác nhận | [NotificationListener.java:26-27](../src/main/java/com/kindergarten/warehouse/listener/NotificationListener.java#L26-L27) |
| Download chặn non-public cho mọi người (không IDOR) | ✅ Xác nhận (đính chính) | [ResourceServiceImpl.java:860-864](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceServiceImpl.java#L860-L864) |
| `validateResourceOwnership` check `isAdmin` 2 lần | ✅ Xác nhận | [dòng 782-789](../src/main/java/com/kindergarten/warehouse/service/impl/ResourceServiceImpl.java#L782-L789) |
| Chỉ 3 file test | ✅ Xác nhận | `find src/test` |

> Các phát hiện DB/API mức Trung/Thấp dựa trên khảo sát migration/entity/controller; nên kiểm chứng lại từng dòng trước khi sửa (đặc biệt số dòng có thể dịch chuyển).
