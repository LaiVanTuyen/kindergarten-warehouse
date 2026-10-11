# API CONTRACT V2

Thay thế [API_CONTRACT_V1.md](API_CONTRACT_V1.md). Quy tắc nghiệp vụ:
[BUSINESS_RULES_V1.md](BUSINESS_RULES_V1.md).

**Cột "Trạng thái" là bắt buộc.** V1 mô tả API mong muốn chứ không mô tả API
thật, nên FE gọi `/me/resources` trong khi BE phục vụ `/resources/me` mà không
ai phát hiện. V2 chỉ được ghi những gì đã kiểm chứng trong code, kèm trạng thái
triển khai.

Kiểm chứng lần cuối: **2026-09-05**, đối chiếu trực tiếp với các controller
trong `src/main/java/com/kindergarten/warehouse/controller/`.

## Chú giải trạng thái

| Ký hiệu | Nghĩa |
|---|---|
| ✅ | Đã có trong code và khớp V1 rules — không cần đổi |
| ⚠️ | Đã có nhưng **phải sửa** để hợp V1 rules |
| ❌ | **Chưa có** — phải viết mới |

---

## 0. Quy ước chung

### 0.1 Vỏ response

Mọi endpoint trả `ApiResponse<T>`:

```json
{
  "code": 1000,
  "message": "Banner list retrieved successfully",
  "result": { },
  "timestamp": "2026-09-05T06:51:00"
}
```

Trường dữ liệu tên là **`result`**, không phải `data`. FE hiện có đoạn
`if (res.result && !res.data) res.data = res.result;` — đó là tương thích ngược
với V1, cần dọn khi FE chuyển hẳn sang `result`.

### 0.2 Mã lỗi

`ErrorCode` đã chuẩn hoá sẵn, giữ nguyên trong V2:

| Code | HTTP | Ý nghĩa |
|---|---|---|
| 1002 | 400 | Lỗi validation |
| 9001 | 400 | Request không hợp lệ |
| 9002 | 405 | Method không được hỗ trợ |
| 9003 | 415 | Media type không được hỗ trợ |
| 9004 | 404 | Đường dẫn không tồn tại |
| 1011 / 1009 | 401 | Chưa xác thực |
| 1012 | 403 | Không đủ quyền |
| 1015 / 1016 | 429 | Vượt số lần thử |
| 2003 | 404 | Không tìm thấy |
| 9999 | 500 | Lỗi không phân loại |

`GlobalExceptionHandler` đã có 14 handler phủ các trường hợp trên. ✅

**⚠️ Một nguồn sai mã lỗi đã xác định.** `IllegalArgumentException` được map
sang `INVALID_REQUEST` → **400**. Nhưng một số service dùng chính exception này
để báo "không tìm thấy", ví dụ `CommentServiceImpl:66`:

```java
.orElseThrow(() -> new IllegalArgumentException("Comment not found"));
```

Kết quả: `DELETE /comments/{id}` với id không tồn tại trả **400 thay vì 404**.

Đã rà toàn bộ `src/main/java` ngày 2026-09-05: **chỉ duy nhất chỗ này**. Sửa
thành `AppException(ErrorCode.*_NOT_FOUND)` là xong. Giữ
`IllegalArgumentException` cho đúng nghĩa "tham số sai".

Quy ước từ V2 trở đi: không dùng `IllegalArgumentException` để báo "không tìm
thấy". Thêm một test cho quy ước này để không tái diễn.

### 0.3 Mã lỗi mới phải thêm vào `ErrorCode`

Dải resource hiện dùng tới 6009, nên ba mã mới lấy tiếp từ 6010:

| Code | HTTP | Hằng số | Dùng khi |
|---|---|---|---|
| 6010 | **410 Gone** | `RESOURCE_ARCHIVED` | Tài nguyên từng công khai, nay đã archive/takedown (BUSINESS_RULES §4.3) |
| 6011 | **401** | `DOWNLOAD_REQUIRES_AUTH` | GUEST gọi endpoint tải file (§8.4). Xem cảnh báo triển khai bên dưới |
| 6012 | **400** | `COPYRIGHT_NOT_CONFIRMED` | Gửi duyệt khi xác nhận bản quyền thiếu hoặc đã mất hiệu lực (§9.3) |

`GlobalExceptionHandler` không cần sửa: handler của `AppException` đã lấy status
từ `errorCode.getHttpStatusCode()`, và `HttpStatus.GONE` dùng được ngay.

Cần thêm message key tương ứng vào `i18n/messages`.

**Cảnh báo cho 6011.** Endpoint tải file phải giữ `permitAll` ở tầng URL
(`SecurityConfig:94` đang đúng), và controller tự kiểm tra `Principal == null`.
Nếu đổi sang `.authenticated()`, Spring Security chặn trước controller và trả
`UNAUTHENTICATED` chung — mã 6011 sẽ **không bao giờ** tới được FE. Chi tiết và
ca kiểm thử: BUSINESS_RULES §8.4.

### 0.4 Header trả file

`GET /resources/{id}/file` phải trả `Content-Disposition` theo RFC 5987/6266,
có **cả hai** tham số để hỗ trợ tên file tiếng Việt:

```http
Content-Disposition: attachment;
    filename="Tai-lieu.pdf";
    filename*=UTF-8''T%C3%A0i%20li%E1%BB%87u.pdf
```

Áp dụng cho cả chế độ `stream` và `accel`. Ở chế độ `accel`, nginx phải chuyển
tiếp nguyên vẹn header do Spring đặt, không được để header từ MinIO đè lên.
Chi tiết: BUSINESS_RULES §8.5.

### 0.5 Phân trang và sắp xếp

Query: `page` (1-based ở FE, BE trừ 1), `size`, `sort`.
`PageableUtils.sanitize` giới hạn field sort theo whitelist **riêng của từng
endpoint** (hằng số `SORT_FIELDS` trong mỗi controller). ✅

#### Field sort không hợp lệ → 400

Đây **không** phải fallback âm thầm. `PageableUtils.sanitizeSort` ném
`AppException(ErrorCode.INVALID_REQUEST)`:

| Tình huống | HTTP | Code |
|---|---|---|
| `sort` trỏ field ngoài whitelist | **400** | 9001 `INVALID_REQUEST` |
| `sort` để trống | 200 | dùng `defaultSort` của endpoint |

**Đề xuất chờ quyết định:** tách mã riêng `INVALID_SORT_FIELD` thay cho 9001
để FE chẩn đoán được ngay là sai tên field, thay vì lẫn với mọi lỗi request
khác. Chưa làm vì đây là **đổi mã lỗi FE đang nhận** — nếu FE đang bắt 9001
thì sẽ hỏng. Cần chốt và đổi cùng lúc ở cả hai phía.

#### ⚠️ `topicCount` và `resourceCount` KHÔNG còn sort được

Hai trường này trước đây là `@Formula` (subquery tương quan trên `Category`
và `Topic`), nên sort được ở tầng SQL. Chúng đã bị gỡ vì làm MySQL bão hoà —
xem [PERF_BASELINE.md](PERF_BASELINE.md) §4.2.

Hiện trạng:

- Chúng là `@Transient`, tính bằng **một truy vấn gom nhóm cho cả trang**.
- **Vẫn có trong response** `CategoryResponse` và `TopicResponse` — hợp đồng
  đọc không đổi.
- **Đã bỏ khỏi `SORT_FIELDS`**, nên `?sort=topicCount` hay
  `?sort=resourceCount` trả **400**.

FE nào đang sort theo hai trường này phải bỏ. Nếu nghiệp vụ thật sự cần sắp
xếp theo số lượng, phải hiện thực hoá thành cột đếm được cập nhật khi ghi
(materialized counter), **không** quay lại `@Formula`.

#### Audit fields trên danh sách Portal

`GET /resources` và search dùng projection hẹp. Bốn trường `createdBy`,
`updatedBy`, `topic.createdBy`, `topic.updatedBy` vẫn giữ trong hình dạng JSON
nhưng luôn là `null`. Hai consumer Portal hiện tại chỉ render resource card và
không hiển thị các trường này; endpoint công khai không được join bảng `users`
chỉ để tải dữ liệu không dùng tới.

`GET /resources/{slug}` và `/admin/resources` vẫn đi qua entity đầy đủ nên các
trường audit có giá trị. Đặc biệt, cột "Người tải lên" của Admin không bị ảnh
hưởng vì Admin gọi endpoint riêng.

### 0.6 Xác thực

JWT trong cookie HttpOnly. CSRF qua cookie `XSRF-TOKEN`.
Cookie `Secure`/`SameSite` theo môi trường — xem BUSINESS_RULES §8.

---

## 1. Auth — `/api/v1/auth`

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| POST | `/login` | GUEST | ✅ | |
| POST | `/register` | GUEST | ✅ | |
| POST | `/logout` | Đã đăng nhập | ✅ | |
| POST | `/forgot-password` | GUEST | ⚠️ | Cần SMTP thật. Hiện `MAIL_USERNAME` trống ⇒ luồng này lỗi. |
| POST | `/reset-password` | GUEST | ⚠️ | Như trên |
| POST | `/verify-email` | GUEST | ⚠️ | Như trên |
| POST | `/resend-verification` | GUEST | ⚠️ | Như trên |

**Chặn cứng:** bốn endpoint ⚠️ không thể nghiệm thu nếu chưa cấu hình SMTP.
Phải xong trước khi tuyên bố Tuần 6 hoàn thành.

---

## 2. Users — `/api/v1/users`

| Method | Path | Quyền | Trạng thái |
|---|---|---|---|
| GET | `/me` | Đã đăng nhập | ✅ |
| PUT | `/profile` | Đã đăng nhập | ✅ |
| PUT | `/change-password` | Đã đăng nhập | ✅ |
| POST | `/avatar` | Đã đăng nhập | ✅ |
| GET | `/` | ADMIN | ✅ |
| POST | `/` | ADMIN | ✅ |
| PUT | `/{id}` | ADMIN | ✅ |
| DELETE | `/{id}` | ADMIN | ✅ |
| PUT | `/{id}/restore` | ADMIN | ✅ |
| PUT | `/{id}/block` | ADMIN | ✅ |
| POST | `/{id}/reset-password/init` | ADMIN | ✅ |
| POST | `/{id}/reset-password/confirm` | ADMIN | ✅ |

---

## 3. Resources — `/api/v1/resources`

| Method | Path | Quyền | Trạng thái | Việc phải làm |
|---|---|---|---|---|
| POST | `/` (multipart) | ADMIN, TEACHER | ⚠️ | Mặc định `DRAFT` thay vì `PENDING`; nhận metadata mới (§3.2) |
| GET | `/` | Công khai | ⚠️ | Áp visibility phân tầng; đổi `types`→`fileTypes`; thêm filter mới |
| GET | `/me` | Đã đăng nhập | ⚠️ | Thêm lọc theo `status` cho các tab Draft/Pending/Approved/Rejected/Archived |
| GET | `/{slug}` | Công khai | ⚠️ | Áp visibility phân tầng; trả **410** nếu đã archive (§4.3) |
| POST | `/{id}/view` | Công khai | ✅ | |
| GET | `/{id}/file` | **Đã đăng nhập** | ⚠️ | GUEST → **401** `DOWNLOAD_REQUIRES_AUTH` ✅. Chế độ `stream` ✅ (`StreamingResponseBody`); `accel` vẫn ❌ (BUSINESS_RULES §8.3) |

> **Không có endpoint đếm lượt tải riêng.** `GET /{id}/file` tự tăng bộ đếm
> trong `getResourceFileInfo`. `PUT /{id}/download` từng tồn tại và FE gọi song
> song với `/file` — mỗi lượt tải bị đếm **hai lần**. Endpoint đó đã bị bỏ ở cả
> hai phía ngày 2026-09-06.
| PUT | `/{id}` (json) | ADMIN, TEACHER | ⚠️ | Kích hoạt quy tắc về `PENDING` (BUSINESS_RULES §5) |
| PUT | `/{id}` (multipart) | ADMIN, TEACHER | ⚠️ | Như trên |
| POST | `/{id}/thumbnail` | ADMIN, TEACHER | ⚠️ | Như trên |
| PATCH | `/{id}/visibility` | ADMIN, TEACHER | ⚠️ | Nhận thêm `INTERNAL`; **không** đưa về PENDING |
| POST | `/{id}/favorite` | Đã đăng nhập | ✅ | Toggle, trả `{isFavorited: bool}` |
| DELETE | `/{id}` | ADMIN, TEACHER | ✅ | Kiểm tra ownership |
| DELETE | `/bulk` | ADMIN, TEACHER | ✅ | Body là mảng ID `[]` |
| PUT | `/{id}/restore` | ADMIN, TEACHER | ✅ | |
| PATCH | `/bulk-restore` | ADMIN, TEACHER | ✅ | |
| POST | `/draft` | ADMIN, TEACHER | ❌ | Tạo draft **tối thiểu**, trả `resourceId`. Không validate trường bắt buộc |
| PATCH | `/{id}` | ADMIN, TEACHER (chủ sở hữu) | ❌ | Lưu từng bước wizard. **Mọi trường optional.** Đang `DRAFT` thì giữ `DRAFT` |
| POST | `/{id}/submit` | TEACHER (chủ sở hữu) | ❌ | Gửi duyệt: `DRAFT`/`REJECTED` → `PENDING`. **Chỉ ở đây** mới kiểm tra đủ trường bắt buộc **và** xác nhận bản quyền còn hiệu lực → thiếu thì **400** `COPYRIGHT_NOT_CONFIRMED` |
| PATCH | `/{id}/archive` | ADMIN | ❌ | `APPROVED` → `ARCHIVED`. Sau đó `GET /{slug}` trả **410** |

### 3.1 Lưu ý về `/resources/me`

Đường dẫn đúng là **`/api/v1/resources/me`**. FE trước đây gọi
`/api/v1/me/resources` và luôn nhận 404 — đã sửa ngày 2026-09-05 tại
`resource.service.ts:163`. Không tạo alias `/me/resources`.

### 3.1b Ba chỗ chặn luồng draft

Wizard "lưu nháp ở mọi bước" (USER_FLOWS §3.1.1) không triển khai được nếu
không sửa **cả ba** chỗ sau:

| Vị trí | Hiện tại | Phải thành |
|---|---|---|
| **Schema** | `title`, `slug`, `file_url`, `topic_id` đều `NOT NULL`; `slug` còn UNIQUE | Migration nới `NULL` + thêm `CHECK` giữ bất biến — BUSINESS_RULES §4.2.1 |
| `ResourceCreationRequest` | `title` `@NotBlank`, `topicId` `@NotNull` | Bỏ ràng buộc khỏi đường tạo draft. Validate dồn về `submit` |
| `ResourceServiceImpl:481` | Non-admin sửa → **luôn** `setStatus(PENDING)` | `DRAFT` giữ `DRAFT`; `REJECTED` giữ `REJECTED`; `APPROVED` → `PENDING` |

Chỗ đầu là blocker cứng ở tầng database — không nới thì `POST /resources/draft`
chết ngay ở `INSERT`, bất kể request đã bỏ validation.

Chỗ thứ ba dễ bỏ sót. Logic hiện tại đúng cho tài liệu đã duyệt — comment
trong code ghi rõ "ZERO TRUST": uploader sửa nội dung thì phải duyệt lại. Nhưng
khi thêm `DRAFT`, đúng dòng đó sẽ khiến **mỗi lần lưu nháp trở thành một lần
gửi duyệt ngoài ý muốn**, và mỗi lần sửa sau khi bị từ chối cũng vậy.

`slug` sinh **khi submit**, không sinh lúc tạo nháp. Không dùng giá trị giả như
`"Untitled"` hay slug tạm.

Trường `status` do client gửi trong `ResourceUpdateRequest` **đã được chặn đúng**
(chỉ ADMIN đổi được) — giữ nguyên cơ chế đó khi thêm `DRAFT`.

### 3.2 Trường metadata bổ sung

Thêm vào `ResourceCreationRequest`, `ResourceUpdateRequest`, `ResourceResponse`:

**Phân loại và mô tả:** `documentType`, `learningDomains[]`,
`teachingMethods[]`, `audiences[]`, `tags[]`, `objectives`, `pageCount`,
`language`, `schoolYear`.

**Nguồn gốc:** `authorName`, `sourceName`, `sourceUrl`, `licenseType`.

**Bản quyền — lưu vết, không chỉ checkbox** (BUSINESS_RULES §9):

| Trường | Chiều | Ghi chú |
|---|---|---|
| `copyrightDeclaration` | request + response | `OWN_WORK` \| `AUTHORIZED` \| `PUBLIC_DOMAIN` \| `HAS_PERMISSION` \| `EXTERNAL_SOURCE` |
| `copyrightConfirmedAt` | **chỉ response** | Server ghi, client không gửi lên |
| `copyrightConfirmedBy` | **chỉ response** | Server lấy từ người đang đăng nhập |
| `copyrightTermsVersion` | **chỉ response** | Server ghi phiên bản điều khoản hiện hành |

Ba trường `confirmedAt`/`confirmedBy`/`termsVersion` **không được nhận từ
client** — nếu client gửi lên thì bỏ qua. Cho client tự đặt thời điểm hoặc
người xác nhận là làm mất giá trị pháp lý của bản ghi.

Với `EXTERNAL_SOURCE` và `AUTHORIZED`, `sourceName` là **bắt buộc**.

**Kiểm duyệt (chỉ response):** `moderatedBy`, `moderatedAt`, `archivedAt`.

### 3.3 Filter của `GET /resources`

Hiện có: `keyword`, `topicId`, `categoryId`, `ageGroupId`, `topicSlugs[]`,
`categorySlugs[]`, `ageSlugs[]`, `types[]`, `status`, `createdBy`.

| Thay đổi | Chi tiết |
|---|---|
| ⚠️ Đổi tên | `types` → `fileTypes`. Tên hiện tại gợi ý `resourceType` nhưng thực chất lọc theo `fileType` — đúng cái nhầm lẫn ba loại "type" mô tả ở BUSINESS_RULES §6.2 |
| ❌ Thêm | `documentTypes[]`, `learningDomains[]`, `teachingMethods[]`, `audiences[]`, `tags[]`, `resourceTypes[]` |
| ❌ Thêm | `sort`: `newest` \| `mostViewed` \| `topRated` |

Toàn bộ filter phải đồng bộ vào URL để chia sẻ được đường dẫn kết quả tìm kiếm.

---

## 4. Favorites

`POST /resources/{id}/favorite` đã có ✅. Thiếu hai endpoint đọc — đây chính là
lý do FE đang mock danh sách rỗng trong `favorites.service.ts`.

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| GET | `/api/v1/favorites` | Đã đăng nhập | ❌ | Danh sách phân trang tài nguyên đã lưu |
| GET | `/api/v1/favorites/ids` | Đã đăng nhập | ❌ | Mảng id, để FE tô nút "đã lưu" |

FE hiện gọi `/me/favorites` và `/me/favorites/ids` — **phải đổi** sang đường dẫn
trên. Bảng `favorites`, entity `Favorite` và `FavoriteRepository` đã có sẵn nên
chỉ cần service + controller.

---

## 5. Ratings — toàn bộ mới

Tách khỏi `comments`. Thiết kế:
[MIGRATION_RATINGS_DESIGN.md](MIGRATION_RATINGS_DESIGN.md).

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| PUT | `/api/v1/resources/{id}/rating` | Đã đăng nhập | ❌ | Body `{score: 1..5}`. Upsert theo `(user, resource)` |
| DELETE | `/api/v1/resources/{id}/rating` | Đã đăng nhập | ❌ | Gỡ đánh giá của chính mình |
| GET | `/api/v1/resources/{id}/rating/me` | Đã đăng nhập | ❌ | Điểm hiện tại của người đang đăng nhập |

Dùng `PUT` chứ không `POST` vì thao tác là idempotent upsert.

---

## 6. Comments — `/api/v1/comments`

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| POST | `/` | Đã đăng nhập | ⚠️ | **Bỏ** trường `rating` khỏi `CreateCommentRequest` |
| GET | `/` | Công khai | ⚠️ | Bỏ `rating` khỏi `CommentResponse` |
| DELETE | `/{id}` | Chủ sở hữu, ADMIN | ⚠️ | Sau khi tách: **không** được đụng tới `ratings` |

---

## 7. Reports — toàn bộ mới

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| POST | `/api/v1/resources/{id}/report` | Đã đăng nhập | ❌ | Body `{reason, detail}` |
| GET | `/api/v1/admin/reports` | ADMIN | ❌ | Hàng đợi xử lý, lọc theo trạng thái |
| PATCH | `/api/v1/admin/reports/{id}/resolve` | ADMIN | ❌ | Body `{action, note}` — `action`: `DISMISS` \| `TAKEDOWN` |

`TAKEDOWN` phải ẩn tài nguyên ngay, ghi audit, **và thông báo cho người upload**
(BUSINESS_RULES §9).

---

## 8. Admin resources — `/api/v1/admin/resources`

Toàn bộ controller yêu cầu `ADMIN`.

| Method | Path | Trạng thái | Ghi chú |
|---|---|---|---|
| GET | `/` | ✅ | Hàng đợi kiểm duyệt |
| PATCH | `/{id}/approve` | ⚠️ | Bổ sung ghi `resource_moderation_history` |
| PATCH | `/{id}/reject` | ⚠️ | Như trên; `reason` bắt buộc |
| PATCH | `/bulk-approve` | ⚠️ | Body `{resourceIds: []}` |
| PATCH | `/bulk-reject` | ⚠️ | Body `{resourceIds: [], reason}` |
| GET | `/{id}/moderation-history` | ❌ | Lịch sử kiểm duyệt của một tài nguyên |

`BulkResourceRequest` đã có `@NotEmpty` và `@Size(min=1, max=1000)` ✅ — không
cần đổi.

---

## 9. Taxonomy

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| GET | `/api/v1/categories` | Công khai | ⚠️ | Áp visibility phân tầng |
| GET | `/api/v1/topics` | Công khai | ⚠️ | Như trên |
| GET | `/api/v1/age-groups` | Công khai | ⚠️ | Bổ sung "Nhà trẻ (dưới 3 tuổi)" |
| GET | `/api/v1/document-types` | Công khai | ❌ | |
| GET | `/api/v1/learning-domains` | Công khai | ❌ | |
| GET | `/api/v1/teaching-methods` | Công khai | ❌ | |
| GET | `/api/v1/audiences` | Công khai | ❌ | |
| GET | `/api/v1/tags?q=` | Công khai | ❌ | Gợi ý khi nhập tag |

CRUD của Category/Topic (ADMIN) đã có đủ ✅ — xem `CategoryController`,
`TopicController`. Bốn taxonomy mới ở V1 **chỉ đọc**, dữ liệu cố định bằng
seeder, chưa có màn quản trị.

### 9.1 Hai trường đếm trong Category và Topic

| Trường | Nằm ở | Trong response? | Sort được? |
|---|---|---|---|
| `topicCount` | `CategoryResponse` | ✅ có | ❌ **không** — trả 400 |
| `resourceCount` | `TopicResponse` | ✅ có | ❌ **không** — trả 400 |

Chi tiết và lý do: §0.5. Tóm tắt: hai `@Formula` cũ chạy như subquery tương
quan **trên từng dòng trả về** nên làm MySQL bão hoà; nay thay bằng một truy
vấn gom nhóm cho cả trang. Giá trị vẫn trả về đầy đủ, chỉ mất khả năng sort.

Cả hai endpoint danh sách Category và Topic đều đã áp visibility phân tầng và
lọc theo người xem — Category/Topic `INTERNAL` không hiện với khách
(BUSINESS_RULES §3.5, §3.6.1).

---

## 10. Banners — `/api/v1/banners`

| Method | Path | Quyền | Trạng thái |
|---|---|---|---|
| GET | `/` | Công khai | ⚠️ Áp visibility theo người xem |
| GET | `/all` | ADMIN | ✅ |
| POST | `/` | ADMIN | ✅ |
| PUT | `/{id}` | ADMIN | ✅ |
| PATCH | `/reorder` | ADMIN | ✅ |
| PUT | `/{id}/toggle` | ADMIN | ✅ |
| DELETE | `/{id}` | ADMIN | ✅ |

---

## 11. Audit logs — `/api/v1/audit-logs`

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| GET | `/` | ADMIN | ✅ | |
| GET | `/export` | ADMIN | ✅ | |

Bổ sung ghi audit cho các hành động mới: `submit`, `archive`, `takedown`,
`report resolve`, `visibility change`, và mọi lần Admin sửa tài nguyên đã duyệt.

---

## 12. Dashboard — số liệu thật (cập nhật 11/10/2026)

`GET /api/v1/admin/dashboard` yêu cầu ADMIN, trả một snapshot gồm `stats`, `trend`, `pending`, `topResources`, `topTeachers`, `topicDistribution`, `activity`. FE đã bỏ dữ liệu mock.

Tham số `period` và khoảng ngày dùng cho tài nguyên/người dùng mới, đóng góp và phân bố chủ đề. Tổng tài nguyên/người dùng và lượt xem là số hiện tại/tích lũy. Lượt xem gồm bộ đếm đang chờ ghi từ Redis. Không có lịch sử lượt xem theo ngày: `trend.views` rỗng và phần tăng trưởng chưa có dữ liệu trả null.

Duyệt/từ chối trên Dashboard dùng endpoint kiểm duyệt hiện có, ID tài nguyên là chuỗi UUID.

---
## 13. Tổng hợp khối lượng

Đếm từ các bảng ở trên:

| Trạng thái | Số endpoint |
|---|---|
| ✅ Giữ nguyên | 30 |
| ⚠️ Phải sửa | 24 |
| ❌ Viết mới | 21 |
| **Tổng** | **75** |

Phân bố theo nhóm:

| Nhóm | ✅ | ⚠️ | ❌ |
|---|---|---|---|
| Auth | 3 | 4 | 0 |
| Users | 12 | 0 | 0 |
| Resources | 6 | 9 | 4 |
| Favorites | 0 | 0 | 2 |
| Ratings | 0 | 0 | 3 |
| Comments | 0 | 3 | 0 |
| Reports | 0 | 0 | 3 |
| Admin resources | 1 | 4 | 1 |
| Taxonomy | 0 | 3 | 5 |
| Banners | 6 | 1 | 0 |
| Audit logs | 2 | 0 | 0 |
| Dashboard | 0 | 0 | 3 |

Phần lớn ⚠️ đến từ hai thay đổi xuyên suốt: **visibility phân tầng** và
**quy tắc quay về PENDING**. Nên làm hai thứ đó thành hàm dùng chung rồi áp
vào từng endpoint, thay vì sửa rải rác 24 chỗ.

---

## 14. Quy tắc duy trì tài liệu này

1. Endpoint mới → thêm dòng vào bảng **trong cùng PR**, không để sau.
2. Đổi đường dẫn/tên trường → cập nhật bảng **trước** khi sửa FE.
3. Trước mỗi lần phát hành Demo, chạy lại đối chiếu controller ↔ tài liệu.
4. Không ghi endpoint "dự kiến có" mà không đánh dấu ❌.

## 15. Preview tài liệu — đã triển khai 2026-10-11

Hai endpoint bổ sung, không thay thế endpoint tải file gốc:

| Method | Path | Trạng thái | Quyền / kết quả |
| --- | --- | --- | --- |
| GET | `/resources/{id}/preview` | ✅ | Guard quyền xem; trả `ApiResponse<PreviewResponse>` |
| GET | `/resources/{id}/preview/pages/{page}` | ✅ | Guard lại từng trang; trả JPEG, `Cache-Control: no-store` |

`PreviewResponse`: `status` (`PROCESSING`, `READY`, `FAILED`, `UNSUPPORTED`, `BUSY`),
`pageCount`, `totalPages`, `truncated`, `message`. Trang đánh số từ 1.
Khách xem được bản preview của tài liệu có quyền xem, nhưng `/resources/{id}/file`
vẫn yêu cầu đăng nhập. Preview không trả URL file gốc và không tăng bộ đếm tải.
DRAFT không có preview; thiếu quyền trả 404 trước khi xét ARCHIVED/410.
Trang ngoài phạm vi hoặc chưa sẵn sàng trả 404. Tối đa 100 trang preview.
Chi tiết xử lý, giới hạn và nghiệm thu: [DOCUMENT_PREVIEW_V1.md](DOCUMENT_PREVIEW_V1.md).


## 16. Khám phá, báo cáo vi phạm, thông báo — triển khai 2026-10-11

Mọi response bọc trong `ApiResponse` (`result`). Phân trang dùng `page` (0-based) và `size`.

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/resources/{slug}/related?size=6` | Công khai | Cùng chủ đề trước, rồi cùng danh mục; loại chính nó trước khi phân trang; áp visibility Portal. `size` tối đa 20. |
| GET | `/resources?tags=a,b&createdBy=` | Công khai | `tags` khớp bất kỳ; `keyword` tìm tiêu đề, mô tả (không dấu theo collation) và tag khớp chính xác. |
| GET | `/tags/popular?limit=20` | Công khai | Chỉ đếm tài liệu khách xem được. |
| GET | `/authors/{id}` | Công khai | Chỉ TEACHER/ADMIN đang hoạt động; không trả email/SĐT/username. Thống kê chỉ gồm tài liệu PUBLIC đã duyệt. |
| GET | `/downloads/me` | Đăng nhập | Lọc visibility trong SQL trước phân trang. Ghi lịch sử khi tải `/resources/{id}/file` thành công. |
| POST | `/reports` | Đăng nhập | `{resourceId, reason, details?}`. `reason`: COPYRIGHT, INAPPROPRIATE, WRONG_INFO, BROKEN_FILE, SPAM, OTHER. Mỗi người một báo cáo OPEN/tài liệu (unique key V25) → 409 mã 6602. |
| GET | `/admin/reports?status=` | ADMIN | |
| GET | `/admin/reports/open-count` | ADMIN | |
| PATCH | `/admin/reports/{id}` | ADMIN | `{action: RESOLVE\|DISMISS, note?, hideResource}`. Khoá dòng; báo cáo đã xử lý → 409 mã 6603. `hideResource` với RESOLVE = gỡ tài liệu: status REJECTED kèm lý do; đổi visibility không đưa tài liệu trở lại. |
| GET | `/notifications`, `/notifications/unread-count` | Đăng nhập | |
| PATCH | `/notifications/{id}/read`, `/notifications/read-all` | Đăng nhập | |

Trường mới trong `ResourceResponse`: `tags[]`, `authorId`, `authorName`. Upload/cập nhật nhận `tags` (lặp hoặc phân cách dấu phẩy; tối đa 10, mỗi tag ≤ 50 ký tự; loại trùng không phân biệt dấu). Khi cập nhật: không gửi `tags` = giữ nguyên, gửi rỗng = xoá hết.

## 17. Bộ học liệu — `/api/v1/collections` (giai đoạn 1, 2026-10-11)

Chỉ TEACHER/ADMIN. Bộ chỉ lưu tham chiếu; mỗi lần đọc, từng tài liệu được kiểm lại bằng `ResourceAccessGuard` cho người đang xem. Tài liệu không xem được trả `available=false`, `resource=null` (không lộ tiêu đề).

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/collections/me` | Bộ của tôi, mới sửa trước. |
| GET | `/collections/shared?keyword=&ageGroupId=` | Bộ SHARED của mọi giáo viên. |
| GET | `/collections/me/membership?resourceId=` | Bộ của tôi + cờ `containsResource` (hộp thoại "Thêm vào bộ"). |
| GET | `/collections/{id}` | Chủ sở hữu; SHARED thì mọi TEACHER/ADMIN; ADMIN xem mọi bộ. Không có quyền → 404 (6801). |
| POST | `/collections` | `{title, description?, ageGroupId?, topicId?, visibility: PRIVATE\|SHARED}`. Tối đa 200 bộ/người (6807). |
| PUT | `/collections/{id}` | Như trên + `version` bắt buộc; lệch → 409 (6806). |
| DELETE | `/collections/{id}` | Chỉ chủ sở hữu (6802). Không xoá tài liệu trong kho. |
| POST | `/collections/{id}/copy` | Tạo bản PRIVATE cho người gọi, giữ thứ tự và ghi chú, chỉ chép tài liệu người gọi xem được; `sourceCollectionId` trỏ về bộ gốc. |
| POST | `/collections/{id}/items` | `{resourceId, note?}`. Người thêm phải xem được tài liệu (404 nếu không). Trùng → 409 (6803); tối đa 50 mục (6805). |
| PATCH | `/collections/{id}/items/{itemId}` | `{note}` |
| DELETE | `/collections/{id}/items/{itemId}` | Đánh số lại thứ tự. |
| PUT | `/collections/{id}/items/order` | `{itemIds}` phải đúng tập mục hiện có; lệch → 409 (6806), client tải lại. |

Sửa đồng thời được chặn bằng `@Version` trên bộ; mọi thay đổi mục đều tăng version.

## 18. Kế hoạch hoạt động — `/api/v1/plans` (giai đoạn 1, 2026-10-11)

Chỉ TEACHER/ADMIN. Kế hoạch lưu có cấu trúc: các phần chữ là trường riêng; mỗi bước tiến trình là một dòng có `source` (`TEACHER` | `AI_SUGGESTED`, để giai đoạn 2 luôn phân biệt nội dung AI). Học liệu gắn với bước chỉ là tham chiếu, kiểm quyền lại mỗi lần đọc (`available=false`, `resource=null` khi người xem không mở được).

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/plans/me?keyword=` | Kế hoạch của tôi; tìm tên và mục tiêu. |
| GET | `/plans/shared?keyword=&ageGroupId=` | `status=SHARED`. `keyword` tìm trong tên và mục tiêu. |
| GET | `/plans/templates?keyword=&ageGroupId=` | Kế hoạch mẫu (`template=true`). |
| GET | `/plans/{id}` | Chủ sở hữu; SHARED hoặc mẫu thì mọi TEACHER/ADMIN; ADMIN xem mọi kế hoạch. Không có quyền → 404 (6901). |
| POST | `/plans` | Toàn bộ tài liệu (xem bên dưới). Không có `steps` → 4 bước mặc định Khởi động/Khám phá/Trò chơi/Kết thúc; có thêm `collectionId` thì học liệu xem được của bộ vào bước "Khám phá". Tối đa 500 kế hoạch/người (6904). |
| PUT | `/plans/{id}` | Gửi toàn bộ tài liệu kèm `version`; lệch → 409 (6903). Các bước được thay cả khối. Tham chiếu đã có được giữ dù không còn khả dụng; tham chiếu MỚI phải xem được (404 nếu không). |
| DELETE | `/plans/{id}` | Chỉ chủ sở hữu (6902). |
| POST | `/plans/{id}/copy` | Bản DRAFT cho người gọi; chép mục tiêu, chuẩn bị, điều chỉnh, các bước; KHÔNG chép ghi nhận sau buổi học và ngày dạy; chỉ chép học liệu người gọi xem được. Từ mẫu thì giữ nguyên tên. |

Thân request: `{title, ageGroupId?, topicId?, durationMinutes? (1–240), classSize? (1–100), objectives?, preparation?, adaptations?, reflection? (≤4000 ký tự mỗi trường), taughtOn?, status: DRAFT|SHARED, template? (chỉ ADMIN), collectionId? (phải đọc được bộ), steps?: [{title, durationMinutes? (0–240), description?, source?, resourceIds? (≤10)}] (≤20 bước), version?}`.

Xuất PDF: Portal có trang `/plans/{id}/print` (không header/footer, CSS `@media print`, khổ A4); giáo viên dùng "In / Lưu PDF" của trình duyệt. Phần ghi nhận để trống được in thành dòng kẻ để viết tay.

### Bổ sung sau review và chế độ sử dụng trong lớp

- Khi đồng nghiệp đọc kế hoạch chia sẻ hoặc mẫu, `reflection` và `taughtOn` trả null; chủ sở hữu và ADMIN vẫn đọc được. Các phần chuẩn bị, mục tiêu, tiến trình vẫn được chia sẻ.
- Phần tử null trong `steps` bị từ chối bằng validation 400.
- Portal `/plans/{id}/present` dùng GET chi tiết kế hoạch hiện có, kiểm quyền lại khi bắt đầu; không có endpoint trình chiếu mới và không tạo trạng thái chia sẻ công khai.
- Trước buổi học có mở thử học liệu, kiểm tra manifest PDF/Office và phát thử YouTube do giáo viên xác nhận. Không coi iframe tải xong là bằng chứng video phát được.
- Màn hoạt động: chọn/chuyển bước, chọn học liệu, toàn màn hình, đồng hồ theo bước (chạy/tạm dừng/đặt lại), ẩn/hiện hướng dẫn. Hết giờ không tự chuyển bước. Notes mặc định ẩn.
- Đồng hồ dùng deadline tuyệt đối để không chậm khi tab bị treo nền; chuyển bước đặt lại đồng hồ của bước mới. Có phím tắt ←/→, Space, N khi không thao tác trên điều khiển.
- Mở tệp gốc ảnh/âm thanh/video vẫn đi qua API có kiểm quyền và cơ chế bộ đếm/lịch sử tải hiện có; PDF/Office dùng bản xem trước nội bộ.

## 19. Trợ lý AI — `/api/v1/ai` (giai đoạn 2, 2026-10-11)

Chỉ TEACHER/ADMIN. Dùng Claude (`claude-opus-5-5`) qua Java SDK chính thức, structured output. **Tắt khi chưa đặt `ANTHROPIC_API_KEY`** (mọi endpoint trừ `/status` trả 503 mã 7101). Không endpoint nào ghi DB: kết quả là gợi ý, giáo viên xem lại rồi mới lưu bằng API kế hoạch/bộ học liệu.

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/ai/status` | `{enabled, dailyLimit, remainingToday}` |
| POST | `/ai/search` | `{query ≤500}` → `{items: [{resource, reason}], remainingToday}`. Chỉ tài liệu thật người gọi mở được. |
| POST | `/ai/plan-draft` | `{request ≤1000, ageGroupId?, durationMinutes? (1–240), classSize? (1–100)}` → bản nháp kế hoạch (không lưu). |
| POST | `/ai/plan-adjust` | `{instruction ≤500, plan: <LessonPlanRequest — bản đang hiển thị trong trình soạn>}` → toàn bộ kế hoạch đã điều chỉnh (không lưu). |

Quy tắc do backend bảo đảm (không phụ thuộc prompt):
- Ứng viên học liệu được tìm bằng chính quyền của người gọi; id tài liệu AI trả về nằm ngoài danh sách ứng viên (hoặc ngoài kế hoạch, với điều chỉnh) bị loại. Tài liệu không xem được không được mô tả cho AI.
- Mọi trường được cắt theo giới hạn của `LessonPlanRequest` (≤20 bước, ≤10 học liệu/bước, độ dài chữ, 0–240 phút).
- Không gửi tên/email người dùng, không gửi ghi nhận sau buổi học và ngày dạy, không có dữ liệu trẻ.
- Hạn mức `ai.daily-limit-per-user` (mặc định 30 yêu cầu/người/ngày theo giờ Việt Nam, Redis); hết lượt → 429 mã 7102; Redis lỗi → từ chối (7103), không mở cửa tự do.
- Lỗi/timeout/rate limit từ Claude → 502 mã 7103; mô hình từ chối → 422 mã 7104.

Portal: trang `/assistant` (tìm học liệu, soạn bản nháp → "Dùng bản nháp này" tạo kế hoạch DRAFT với mọi bước `AI_SUGGESTED`); trong trình soạn có ô "Nhờ trợ lý điều chỉnh" — chỉ bước bị đổi thành `AI_SUGGESTED`, có hoàn tác; nếu giáo viên sửa trong lúc chờ, kết quả bị bỏ để không ghi đè.

Cấu hình: `ANTHROPIC_API_KEY`, `AI_MODEL` (mặc định `claude-opus-5-5`), `AI_DAILY_LIMIT` (mặc định 30).

## 20. Phối hợp trong trường — giai đoạn 3 (2026-10-11)

Chỉ TEACHER/ADMIN. Quyền đọc/sở hữu kế hoạch dùng lại quy tắc mục 18.

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/plans/library?keyword=&ageGroupId=&approvedOnly=` | Thư viện trường = kế hoạch SHARED; `approvedOnly=true` chỉ lấy kế hoạch đã duyệt chuyên môn. |
| GET | `/plans/{id}/collaboration` | `{teachersUsed, usedByMe, myLastUsedOn, comments[]}` |
| POST | `/plans/{id}/comments` | `{content ≤2000}`. Đọc được kế hoạch là góp ý được (bản nháp của người khác → 404). Chủ kế hoạch nhận thông báo `PLAN_COMMENT` (trừ khi tự góp ý). |
| DELETE | `/plans/{id}/comments/{commentId}` | Tác giả hoặc ADMIN; khác → 403 (6907). |
| POST | `/plans/{id}/usage` | `{usedOn?}` (mặc định hôm nay giờ VN, không được ở tương lai). Mỗi giáo viên một dòng, đếm số lần. |
| POST | `/plans/{id}/review-request` | Chủ sở hữu; kế hoạch phải SHARED và đang NONE/CHANGES_REQUESTED → PENDING; khác → 409 (6905). |
| GET | `/admin/plan-reviews` | ADMIN; hàng đợi PENDING, cũ nhất trước. |
| POST | `/admin/plan-reviews/{id}` | ADMIN; `{version, decision: APPROVE\|REQUEST_CHANGES, note}` (note bắt buộc khi REQUEST_CHANGES). Chỉ thành công nếu kế hoạch còn PENDING **và** đúng `version` người duyệt đã đọc → không duyệt nhầm nội dung chưa xem, hai người duyệt không cùng thắng. Thông báo `PLAN_APPROVED` / `PLAN_CHANGES_REQUESTED` cho chủ. |

Trường mới: `Summary.reviewStatus` (`NONE|PENDING|APPROVED|CHANGES_REQUESTED`), `Detail.reviewNote`.

Quy tắc trạng thái duyệt:
- Gửi duyệt và quyết định duyệt dùng UPDATE có điều kiện, **không tăng `version`** → trình soạn đang mở của giáo viên vẫn lưu được.
- Các cột duyệt là `updatable=false` trên entity: một lần lưu của chủ chạy song song với quyết định duyệt không ghi đè trạng thái duyệt bằng giá trị cũ.
- Chủ sửa **nội dung** (tên, độ tuổi, chủ đề, thời lượng, sĩ số, mục tiêu, chuẩn bị, điều chỉnh, các bước và học liệu) của kế hoạch APPROVED → về NONE (cần duyệt lại). Sửa ghi nhận/ngày dạy hoặc xác nhận bước AI không ảnh hưởng. Chuyển về DRAFT → rút khỏi duyệt.

Portal: tab "Thư viện trường" (lọc đã duyệt, nhãn "Đã duyệt"), khối "Phối hợp" trên trang kế hoạch (gửi duyệt, duyệt/yêu cầu sửa cho ADMIN, "Tôi đã dùng hôm nay", góp ý), trang `/reviews` cho ADMIN.
