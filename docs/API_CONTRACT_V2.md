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
| 1011 / 1009 | 401 | Chưa xác thực |
| 1012 | 403 | Không đủ quyền |
| 1015 / 1016 | 429 | Vượt số lần thử |
| 2003 | 404 | Không tìm thấy |
| 9999 | 500 | Lỗi không phân loại |

`GlobalExceptionHandler` đã có 12 handler phủ các trường hợp trên. ✅

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

#### Trường audit trong `GET /resources` (list và search)

Endpoint list dùng projection hẹp thay vì entity đầy đủ, nên bốn trường audit
có ngữ nghĩa khác endpoint chi tiết. **Đây là quyết định có chủ đích, không
phải hệ quả phụ của tối ưu.**

| Trường | `GET /resources` (list, search) | `GET /resources/{slug}` và `/admin/resources` |
|---|---|---|
| `createdBy` | ⚠️ **luôn `null`** | ✅ có giá trị |
| `updatedBy` | ⚠️ **luôn `null`** | ✅ có giá trị |
| `topic.createdBy` | ⚠️ **luôn `null`** | ✅ có giá trị |
| `topic.updatedBy` | ⚠️ **luôn `null`** | ✅ có giá trị |

Hình dạng JSON **không đổi** — các trường vẫn xuất hiện, chỉ mang giá trị
`null`. FE không mất trường, nhưng **không được dựa vào chúng ở màn danh sách**.

Căn cứ: hai consumer duy nhất của endpoint list Portal là
`portal/home.component` và `portal/resource-list.component`, cả hai render qua
`resource-card` vốn không hiển thị tên người đăng. Màn Admin gọi
`/admin/resources` — đường entity riêng, không đụng projection — nên cột
"Người tải lên" vẫn đủ dữ liệu.

Nguyên tắc: **endpoint công khai chỉ trả tối thiểu dữ liệu thật sự được dùng.**
Lấy thêm bốn cái tên nghĩa là join hoặc truy vấn thêm bảng `users` trên đường
đi công khai — không có lý do khi không ai hiển thị chúng.

Ràng buộc được khoá bằng [`perf/check-query-count.sh`](../perf/check-query-count.sh):
tối đa một truy vấn chạm `users`, và không truy vấn nào select `password`,
`token_version` hay `original_email`.

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
| PATCH | `/{id}/restore` | ADMIN | ✅ |
| PATCH | `/{id}/block` | ADMIN | ✅ |
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
| GET | `/{id}/file` | **Đã đăng nhập** | ⚠️ | GUEST → **401** `DOWNLOAD_REQUIRES_AUTH`. Chuyển sang `stream \| accel` (BUSINESS_RULES §8.3) |
| PUT | `/{id}` (json) | ADMIN, TEACHER | ⚠️ | Kích hoạt quy tắc về `PENDING` (BUSINESS_RULES §5) |
| PUT | `/{id}` (multipart) | ADMIN, TEACHER | ⚠️ | Như trên |
| POST | `/{id}/thumbnail` | ADMIN, TEACHER | ⚠️ | Như trên |
| PATCH | `/{id}/visibility` | ADMIN, TEACHER | ⚠️ | Nhận thêm `INTERNAL`; **không** đưa về PENDING |
| POST | `/{id}/favorite` | Đã đăng nhập | ✅ | Toggle, trả `{isFavorited: bool}` |
| DELETE | `/{id}` | ADMIN, TEACHER | ✅ | Kiểm tra ownership |
| POST | `/bulk-delete` | ADMIN, TEACHER | ✅ | Body `{ids: []}` |
| PATCH | `/{id}/restore` | ADMIN, TEACHER | ✅ | |
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
| PATCH | `/bulk-approve` | ⚠️ | Body `{ids: []}` đã đúng chuẩn |
| PATCH | `/bulk-reject` | ⚠️ | Body `{ids: [], reason}` đã đúng chuẩn |
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
| PATCH | `/{id}/toggle` | ADMIN | ✅ |
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

## 12. Dashboard — toàn bộ mới

FE đang dùng mock in-memory trong `dashboard.service.ts`. Không có endpoint nào
ở BE.

| Method | Path | Quyền | Trạng thái | Ghi chú |
|---|---|---|---|---|
| GET | `/api/v1/admin/dashboard/summary` | ADMIN | ❌ | Tổng tài nguyên, chờ duyệt, người dùng, lượt tải |
| GET | `/api/v1/admin/dashboard/recent` | ADMIN | ❌ | Tài nguyên mới, chờ duyệt |
| GET | `/api/v1/admin/dashboard/top` | ADMIN | ❌ | Tải nhiều, giáo viên đóng góp |

**Cho tới khi ba endpoint này xong, màn Dashboard phải hiển thị nhãn "dữ liệu
mẫu"** — không được để người xem demo hiểu nhầm là số liệu thật.

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
