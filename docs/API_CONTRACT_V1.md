# API Contract v1 (FROZEN) — Kindergarten Warehouse

> **Mục đích:** Nguồn chân lý chung BE ⇄ FE. Khóa hợp đồng API **trước** khi build feature FE để tránh làm lại.
> **Trạng thái:** Đã chốt 3 quyết định nghiệp vụ (xem §0). Các điểm còn lại chốt theo khuyến nghị `DESIGN_REVIEW.md`.
> **Liên quan:** [DESIGN_REVIEW.md](./DESIGN_REVIEW.md) · Ngày: 2026-06-14
>
> Quy ước: 🔴 = **breaking change** với FE hiện tại (phải sửa); 🟡 = thay đổi nhẹ/tương thích; 🟢 = giữ nguyên.

---

## 0. Quyết định đã chốt

| # | Quyết định | Chốt |
|---|---|---|
| D1 | Mô hình trạng thái Category/Topic/Banner | **Hợp nhất sang `visibility` enum** (giống Resource). Bỏ `isActive`. |
| D2 | Chuẩn hóa HTTP method | **Đổi ngay sang chuẩn**: PATCH cho đổi-trạng-thái (restore/visibility/toggle), POST cho view/favorite. |
| D3 | Policy mật khẩu (FE+BE) | **≥8 ký tự, bắt buộc có chữ hoa + chữ thường + số**. Một regex dùng chung register/reset/change. |

---

## 1. Quy ước toàn cục (Global Conventions)

### 1.1 Response envelope 🟡
**Mọi** response (kể cả lỗi, kể cả download lỗi) đều dùng `ApiResponse<T>`:

```jsonc
{
  "code": 1000,            // 1000 = success; khác 1000 = mã lỗi (xem §4)
  "message": "string",     // đã i18n theo Accept-Language
  "result": { },           // payload; null khi lỗi
  "timestamp": "2026-06-14T10:00:00"
}
```

- **Bỏ hẳn `data`.** FE dọn các chỗ `result ?? data` → chỉ dùng `result`. (review [mục 2])
- Field thành công luôn là `result`. `code=1000` cho mọi success (HTTP status mang ngữ nghĩa chính).
- **List luôn là `Page<DTO>`** trong `result` (cấu trúc Spring Page: `content`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`, `numberOfElements`).

### 1.2 Pagination & sort 🔴 (chuẩn hóa)
**Một quy ước duy nhất cho mọi endpoint list:**

```
?page={int=0}&size={int=10, max=100}&sort={field},{asc|desc}
```

- `sort` theo cú pháp Spring Pageable: `sort=createdAt,desc` (có thể lặp nhiều `sort`).
- **Bỏ** `sortBy` + `sortDir` rời rạc (category/audit đang dùng) → thống nhất `sort`.
- **Whitelist `sort` field** ở BE; field ngoài whitelist → `400 / 9001` thay vì 500.
- 🔴 **FE phải gỡ workaround gửi `desc` vào `sortBy`** (review [API-4]); BE gỡ đoạn vá tương ứng ở `CategoryController`.
- `size` vượt 100 → bị cap về 100 (không lỗi). Giới hạn này được công bố chính thức.

### 1.3 Auth & CSRF 🟢 (giữ, có ghi chú)
- JWT trong cookie `accessToken` (HttpOnly). FE **không** đọc được token — đúng thiết kế.
- CSRF: double-submit **raw token** — cookie `XSRF-TOKEN` (không HttpOnly) → FE gắn header `X-XSRF-TOKEN` = đúng giá trị cookie. BE dùng `CsrfTokenRequestAttributeHandler` (KHÔNG Xor) nên so khớp raw — đã verify: gửi header ⇒ 200, thiếu header ⇒ 403.
  - ⚠️ **Cross-origin gotcha (FE):** Angular `HttpXsrfInterceptor` mặc định **không** gắn `X-XSRF-TOKEN` cho request URL tuyệt đối khác origin (vd FE `:4200` → API `:8080`). Bắt buộc dùng **dev proxy** (`/api` → `localhost:8080`, request thành relative) **hoặc** interceptor tự đọc cookie `XSRF-TOKEN` và set header thủ công. BE không cần sửa.
  - ⚠️ Nếu sau này BE chuyển token sang response header (review [SEC-2]) sẽ thông báo đổi contract riêng — **chưa đổi ở v1**.
- Endpoint cần CSRF token: mọi method thay đổi trạng thái (POST/PUT/PATCH/DELETE) trừ `login`/`register`.

### 1.4 Method semantics 🔴 (D2)
| Loại thao tác | Method chuẩn |
|---|---|
| Tạo mới | `POST` (trả **201**) |
| Thay thế toàn bộ (full update) | `PUT` |
| Đổi một phần / đổi trạng thái (restore, visibility, toggle, approve, reject) | `PATCH` |
| Tăng đếm (view), bật/tắt yêu thích (favorite) | `POST` |
| Xóa | `DELETE` (đơn) / `POST /…/bulk-delete` (hàng loạt, có body) |

---

## 2. Thay đổi hợp đồng theo endpoint (FE phải đồng bộ)

### 2.1 Comment — chuyển sang JSON body 🔴 ([API-2])
**Trước:** `POST /api/v1/comments?resourceId=&content=&rating=` (query param).
**Sau (chốt):**
```
POST /api/v1/comments         (201)   body: CreateCommentRequest
GET  /api/v1/comments?resourceId={id}&page=&size=&sort=createdAt,desc
DELETE /api/v1/comments/{id}
```
`CreateCommentRequest`:
```jsonc
{ "resourceId": "uuid", "content": "string (1..2000)", "rating": 5 }  // rating 1..5, default 5
```
> FE `comment.service.ts` đã gửi JSON `{content, rating}` → **bổ sung `resourceId` vào body**, bỏ query param. Sau đổi này FE và BE khớp.

### 2.2 AuditLog — trả DTO, không trả entity 🔴 ([API-2])
**`AuditLogResponse`** (đóng băng field, FE map theo đây):
```jsonc
{
  "id": 123,
  "action": "APPROVE",          // string enum, xem §3.3
  "username": "teacher_hoa",
  "target": "RESOURCE_STATUS",
  "detail": "string|null",
  "ipAddress": "string|null",
  "userAgent": "string|null",
  "timestamp": "2026-06-14T10:00:00"
}
```
- `GET /api/v1/audit-logs` → `result: Page<AuditLogResponse>`.
- `GET /api/v1/audit-logs/export` → `text/csv` (đính kèm). Cột CSV theo đúng field trên.
- Field giữ y hệt entity hiện tại nên FE model **gần như không đổi**, nhưng từ nay BE cam kết qua DTO (thêm cột entity sẽ không tự lộ ra API).

### 2.3 Download file — lỗi luôn JSON, mở cho owner/admin 🔴 ([API-2] + [SEC-4])
```
GET /api/v1/resources/{id}/file
```
- **Thành công:** stream nhị phân + header `Content-Disposition: attachment; filename=...`, `Content-Type`, `Content-Length`.
- **Lỗi:** **luôn** trả `ApiResponse` JSON (không nhúng lỗi vào stream). FE đọc JSON để lấy `code`/`message`.
- **Quyền tải (chốt mới):**
  - `PUBLIC + APPROVED + chưa xóa` → ai cũng tải (giữ public).
  - `PENDING`/`PRIVATE`/chưa duyệt → **chỉ owner hoặc ADMIN** tải được (đăng nhập). Người khác/ẩn danh → `6004 RESOURCE_FORBIDDEN`.
  - YouTube → `9001`/`400` (không tải trực tiếp).
- **Rate-limit:** theo IP (ngưỡng BE cấu hình); vượt → `6009 RESOURCE_VIEW_RATE_LIMIT_EXCEEDED` (HTTP 429) kèm `Retry-After`.
> 🔴 FE: nút Tải/Xem ở **Teacher My Resources** và **Admin preview drawer** giờ hoạt động với file chưa duyệt **khi đã đăng nhập đúng quyền**. FE cần gửi kèm cookie (đang có) và xử lý 429/403.

### 2.4 Method đổi trạng thái — đồng bộ verb 🔴 (D2 / [API-1])
| Endpoint | Trước | Sau (chốt) |
|---|---|---|
| Khôi phục resource | `PUT /resources/{id}/restore` | `PATCH /resources/{id}/restore` |
| Khôi phục resource hàng loạt | `PATCH /resources/bulk-restore` | `PATCH /resources/bulk-restore` 🟢 |
| Khôi phục user | `PUT /users/{id}/restore` | `PATCH /users/{id}/restore` |
| Block/unblock user | `PUT /users/{id}/block` | `PATCH /users/{id}/block` |
| Khôi phục category/topic | `PATCH …/restore` | `PATCH …/restore` 🟢 |
| Đổi visibility resource | `PATCH /resources/{id}/visibility` | `PATCH …/visibility` 🟢 |
| Tăng view | `PUT /resources/{id}/view` | `POST /resources/{id}/view` |
| Toggle favorite | `POST /resources/{id}/favorite` | `POST …/favorite` 🟢 |
| Toggle banner | `PUT /banners/{id}/toggle` | `PATCH /banners/{id}/toggle` |
| Approve/Reject (admin) | `PATCH /admin/resources/{id}/approve\|reject` | giữ `PATCH` 🟢 |
| Xóa hàng loạt resource/category | `DELETE …/bulk` (+body) | `POST …/bulk-delete` (+body) |

> 🔴 FE: cập nhật verb trong `resource.service`, `banner.service`, `category.service`, `user.service` theo bảng trên (sửa 1 lần).

### 2.5 Bulk payload — chuẩn hóa DTO 🟡 ([API-3])
Mọi thao tác hàng loạt dùng một DTO thống nhất:
```jsonc
// bulk-delete / bulk-restore / bulk-approve
{ "ids": ["...", "..."] }                 // 1..1000 phần tử

// bulk-reject (kèm lý do dùng chung cho cả lô)
{ "ids": ["...", "..."], "reason": "string" }
```
- Resource bulk-reject dùng field `ids` + `reason` (lý do dùng chung — đã thống nhất là chấp nhận được).
- Vượt 1000 phần tử hoặc rỗng → `1002 VALIDATION_ERROR`.
- `PATCH /banners/reorder` không phải bulk CRUD; endpoint này nhận mảng ID có thứ tự (`[3,1,2]`) để biểu diễn vị trí.
> Hiện FE/BE chỗ dùng `resourceIds`, chỗ dùng `List<String>` inline → **đổi hết về `ids`** cho nhất quán. (Nếu muốn giữ `resourceIds` cho resource thì phải ghi rõ; khuyến nghị `ids` cho mọi nơi.)

---

## 3. Mô hình dữ liệu (DTO field — đóng băng)

### 3.1 `visibility` thay cho `isActive` 🔴 (D1 / [DB-1])
Áp dụng cho **Category, Topic, Banner, Resource**. Bỏ `isActive` khỏi API.
```
visibility: "PUBLIC" | "PRIVATE"
```
- Lọc admin: thay `status=ACTIVE|INACTIVE` → `visibility=PUBLIC|PRIVATE`.
- Quy tắc hiển thị public của **Resource** vẫn cần đồng thời: `visibility=PUBLIC` **và** `status=APPROVED` **và** `isDeleted=false`.
- Banner public: `visibility=PUBLIC` + còn hạn (`startDate/endDate`) + `isDeleted=false`.
> 🔴 FE: đổi model `Category.isActive/Topic.isActive/Banner.isActive` → `visibility`; cập nhật toggle bật/tắt (PATCH toggle vẫn chuyển PUBLIC⇄PRIVATE) và bộ lọc admin.
> ⚙️ BE: cần migration mới (Flyway Vxx) chuyển `is_active` → `visibility` cho 3 bảng + bỏ cột `is_active`. FE chờ BE deploy migration trước khi đổi model.

### 3.2 `ResourceStatus` & `Visibility` & `ResourceType` & `FileType` (enum string) 🟢
```
status:       "PENDING" | "APPROVED" | "REJECTED"
visibility:   "PUBLIC" | "PRIVATE"
resourceType: "FILE" | "YOUTUBE" | "EXTERNAL_LINK"
fileType:     "VIDEO" | "DOCUMENT" | "PDF" | "EXCEL" | "POWERPOINT" | "IMAGE"
```
BE hỗ trợ file video (`mp4/mov/avi`), tài liệu (`doc/docx`), bảng tính (`xls/xlsx`), PDF, PowerPoint (`ppt/pptx`) và ảnh (`jpg/jpeg/png/webp`).

### 3.3 AuditLog `action` (string ổn định) 🟢
`CREATE, UPDATE, DELETE, LOGIN, LOGOUT, VIEW, UPLOAD, DOWNLOAD, APPROVE, REJECT, RESTORE, APPROVE_BULK, REJECT_BULK, DELETE_BULK, RESTORE_BULK, OTHER`.

---

## 4. Bảng mã lỗi (FROZEN — FE switch theo đây)

`code=1000` = success. Lỗi → HTTP status tương ứng + `code` dưới đây (giữ ổn định, không đổi số).

| code | Hằng | HTTP | Ý nghĩa |
|---|---|---|---|
| 9999 | UNCATEGORIZED_EXCEPTION | 500 | Lỗi nội bộ |
| 9001 | INVALID_REQUEST | 400 | Request không hợp lệ |
| 9002 | METHOD_NOT_ALLOWED | 405 | Sai HTTP method cho endpoint |
| 9003 | UNSUPPORTED_MEDIA_TYPE | 415 | Sai Content-Type (vd gửi JSON vào endpoint chỉ nhận multipart) |
| 1001 | INVALID_KEY | 400 | Validation |
| 1002 | VALIDATION_ERROR | 400 | Validation field (kèm chi tiết) |
| 1009 | UNAUTHORIZED | 401 | Chưa cấp quyền |
| 1011 | UNAUTHENTICATED | 401 | Chưa đăng nhập / sai đăng nhập |
| 1012 | FORBIDDEN | 403 | Không đủ quyền |
| 1013 | INVALID_PASSWORD | 400 | Sai mật khẩu |
| 1014 | INVALID_OTP | 400 | OTP sai |
| 1015 | OTP_ATTEMPT_EXCEEDED | 429 | Vượt số lần thử OTP |
| 1016 | LOGIN_ATTEMPT_EXCEEDED | 429 | Vượt số lần login |
| 1017 | EMAIL_NOT_VERIFIED | 403 | Chưa xác thực email |
| 1018 | ACCOUNT_BLOCKED | 403 | Tài khoản bị khóa |
| 1019 | ACCOUNT_DELETED | 403 | Tài khoản đã xóa |
| 1020 | ACCOUNT_PENDING | 403 | Tài khoản chờ duyệt |
| 2001 | USER_EXISTED | 409 | Username đã tồn tại |
| 2003 | USER_NOT_FOUND | 404 | Không thấy user |
| 2004 | EMAIL_EXISTED | 409 | Email đã tồn tại |
| 2005 | CANNOT_MODIFY_SELF | 400 | Không tự sửa/xóa/hạ quyền mình |
| 2006 | LAST_ADMIN_PROTECTED | 400 | Bảo vệ admin cuối |
| 2007 | INVALID_ROLE | 400 | Role không hợp lệ |
| 3001 | CATEGORY_NOT_FOUND | 404 | |
| 4001 | TOPIC_NOT_FOUND | 404 | |
| 5001 | BANNER_NOT_FOUND | 404 | |
| 6001 | RESOURCE_NOT_FOUND | 404 | |
| 6002 | FILE_UPLOAD_ERROR | 400 | Lỗi upload file |
| 6003 | FILE_TYPE_INVALID | 400 | Định dạng file không hỗ trợ |
| 6004 | RESOURCE_FORBIDDEN | 403 | Không phải chủ sở hữu / không đủ quyền |
| 6005 | INVALID_YOUTUBE_LINK | 400 | Link YouTube không hợp lệ |
| 6006 | INVALID_IMAGE_FORMAT | 400 | Thumbnail sai định dạng |
| 6007 | THUMBNAIL_TOO_LARGE | 400 | Thumbnail quá lớn |
| 6008 | AVATAR_TOO_LARGE | 400 | Avatar quá lớn |
| 6009 | RESOURCE_VIEW_RATE_LIMIT_EXCEEDED | 429 | Vượt rate-limit view/download (kèm `Retry-After`) |
| 6501 | AGE_GROUP_NOT_FOUND | 404 | |
| 7001 | DUPLICATE_SLUG | 409 | Slug trùng |
| 7002 | DUPLICATE_NAME | 409 | Tên trùng |
| 7003 | DUPLICATE_ENTRY | 409 | Bản ghi trùng |
| 7004 | CONCURRENT_MODIFICATION | 409 | Xung đột optimistic lock (sẽ xuất hiện khi BE thêm `@Version` cho Resource — [DB-6]/[ARC-4]) |
| 8001 | FIREBASE_INIT_ERROR | 500 | |
| 8002 | STORAGE_ERROR | 500 | Lỗi MinIO/storage |
| 8003 | EXTERNAL_SERVICE_ERROR | 500 | |

> 🟡 FE: chuẩn bị xử lý **7004** (hiện 2 admin sửa cùng resource có thể xung đột) và **6009** ở luồng download (trước chỉ có ở view).

---

## 5. Policy mật khẩu dùng chung 🔴 (D3 / [SEC-5])

**Luật chung cho register / reset-password / change-password:**
- Độ dài **≥ 8** ký tự.
- Bắt buộc có: **1 chữ hoa, 1 chữ thường, 1 chữ số**.
- Không bắt buộc ký tự đặc biệt.

Regex tham chiếu (FE + BE dùng cùng):
```
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$
```
- BE trả `1013 INVALID_PASSWORD` (hoặc `1002` ở validation form) khi không đạt.
> 🔴 FE: thống nhất validator ở các form tạo/đổi/reset mật khẩu (`register/settings/profile/reset-password`) theo regex trên. Login chỉ kiểm tra email/password không rỗng để tài khoản dùng mật khẩu cũ vẫn đăng nhập được.

---

## 6. Checklist khóa contract (đối chiếu mục 1 của bạn)

| Điểm cần chốt | Trạng thái | Mục contract |
|---|---|---|
| Envelope `result` (bỏ `data`), list là `Page<DTO>`, lỗi luôn `ApiResponse` (kể cả download) | ✅ Chốt | §1.1, §2.3 |
| Comment dùng `@RequestBody` DTO | ✅ Chốt | §2.1 |
| AuditLog trả DTO (đóng băng field) | ✅ Chốt | §2.2 |
| Method chuẩn (PATCH đổi-trạng-thái, POST view/favorite) | ✅ Chốt (D2) | §1.4, §2.4 |
| Pagination `page,size,sort=field,dir` + whitelist sort | ✅ Chốt | §1.2 |
| `visibility` vs `is_active` cho category/topic/banner | ✅ Chốt: hợp nhất `visibility` (D1) | §3.1 |
| Download non-public cho owner/admin + rate-limit | ✅ Chốt | §2.3 |
| Policy mật khẩu chung | ✅ Chốt ≥8 + hoa/thường/số (D3) | §5 |
| Giữ ổn định bảng mã lỗi (6001/6004/6005/6006/6007/9001…) | ✅ Chốt (freeze) | §4 |
| Bulk field (`ids` + `reason`) | ✅ Chốt: dùng `ids` | §2.5 |

---

## 7. Thứ tự triển khai đề xuất (tránh FE/BE chặn nhau)

> Mỗi thay đổi 🔴 nên BE deploy trước, FE đổi sau (hoặc thống nhất một cửa sổ release chung). Gợi ý theo độ rủi ro:

1. **BE trước (nền tảng, không phá FE ngay):** thêm DTO AuditLog/Comment, chuẩn hóa download lỗi-JSON, whitelist sort, thêm 7004/`@Version` cho Resource.
2. **BE migration D1 (`is_active→visibility`)** → thông báo FE → **FE đổi model + filter**.
3. **Đổi method D2** (BE mở route mới/đổi verb) → **FE đổi verb** trong các service (đồng bộ cùng release).
4. **Policy mật khẩu D3** (BE+FE cùng lúc, vì là validate 2 phía).
5. **Download non-public cho owner/admin** → FE bật lại nút tải/xem ở My Resources & Admin drawer.

## 8. Mục KHÔNG chặn build FE (chỉ ghi backlog — mục 3 của bạn)

- **[ARC-3]** Email reject khi rollback → user nhận mail nhưng FE hiện PENDING → **không phải bug FE**. (BE fix bằng `@TransactionalEventListener`.)
- **[ARC-4]** Race `averageRating` → số sao có thể sai → **chờ BE fix**, FE không cứu được.
- **[ARC-2]** File mồ côi khi rollback → có thể `thumbnailUrl/fileUrl` hỏng → **FE giữ fallback ảnh** (đã có ở resource-card).

> Ghi 3 mục này vào backlog để khỏi truy nhầm thành bug FE.
