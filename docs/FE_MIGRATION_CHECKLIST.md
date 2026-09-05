# FE Migration Checklist — bám theo API_CONTRACT_V1

> Bàn giao cho team FE. Liệt kê **chỉ phần FE phải sửa**, tách khỏi BE.
> Nguồn: [API_CONTRACT_V1.md](./API_CONTRACT_V1.md) · [DESIGN_REVIEW.md](./DESIGN_REVIEW.md)
> Quy ước: 🔴 breaking (phải sửa) · 🟡 nhẹ · ✅ BE đã xong (chờ FE đồng bộ) · ⏳ BE chưa làm (chưa cần đụng FE).

---

## A. BE ĐÃ DEPLOY — FE cần đồng bộ ngay

### A1. Comment dùng JSON body thay vì query param ✅🔴
- **BE đã đổi:** `POST /api/v1/comments` giờ nhận `@RequestBody` JSON, **không** còn đọc query param.
- **FE làm:** `comment.service.ts` gửi body
  ```json
  { "resourceId": "<uuid>", "content": "<string ≤2000>", "rating": 5 }
  ```
  - Đảm bảo **`resourceId` nằm trong body** (trước có thể đang để ở query). `rating` optional (BE mặc định 5).
  - Lỗi validation trả `1002` (kèm map field→message) hoặc `9001` nếu JSON hỏng.

### A2. AuditLog trả DTO (field đóng băng) ✅🟡
- **BE đã đổi:** `GET /api/v1/audit-logs` → `result: Page<AuditLogResponse>` (không còn entity thô).
- **FE làm:** `audit-log.model` map theo đúng field — **giữ nguyên tên**, gần như không phải đổi:
  `id, action, username, target, detail, ipAddress, userAgent, timestamp`.
  - Field giờ ổn định qua DTO; BE thêm cột entity sẽ không tự lộ ra → an toàn hơn.

### A3. `visibility` thay cho `isActive` ở Category/Topic/Banner ✅🔴
- **BE đã đổi:** bỏ `isActive` (boolean) → dùng `visibility: "PUBLIC" | "PRIVATE"` cho **Category, Topic, Banner** (đồng bộ Resource). Migration `V22` đã backfill (`true→PUBLIC`, `false→PRIVATE`).
- **FE làm:**
  1. Model: `Category.isActive` / `Topic.isActive` / `Banner.isActive` → **`visibility: 'PUBLIC' | 'PRIVATE'`**.
  2. Request tạo/sửa Category/Topic: gửi `"visibility": "PUBLIC"|"PRIVATE"` (thay cho `isActive: true/false`).
  3. Bộ lọc admin & nhãn hiển thị: `ACTIVE/INACTIVE` → `PUBLIC/PRIVATE` (bật = PUBLIC, tắt = PRIVATE).
  4. Banner toggle: response trả `visibility` mới (PUBLIC⇄PRIVATE) thay vì `isActive`.
  - Quy ước hiển thị: Category/Topic/Banner "đang bật" ⟺ `visibility === 'PUBLIC'`.

### A4. (Không phá FE) các fix BE trong suốt ✅
- `averageRating` giờ tính nguyên tử ([ARC-4]) → số sao ổn định, FE không cần đổi.
- Resource có optimistic lock: khi 2 người sửa cùng resource, BE có thể trả **`7004 CONCURRENT_MODIFICATION` (HTTP 409)** → **FE nên bắt 409/7004** và hiện thông báo "dữ liệu vừa thay đổi, tải lại" + cho retry.

---

### A5. Bulk payload chuẩn hóa `{ids}` ✅🔴 — BE ĐÃ ĐỔI (BE-9)
- **BE đã chuẩn hóa** tất cả nghiệp vụ bulk delete/restore/approve/reject về body `{ "ids": [...] }` (bulk-reject thêm `reason`), đúng contract §2.5. Mảng thô / `resourceIds` cũ giờ **bị 400**.
- **FE làm (đổi lại):**
  - admin `bulk-approve`/`bulk-reject`: `{resourceIds}` → **`{ids}`** (reject: `{ids, reason}`).
  - `resources/bulk-delete`, `resources/bulk-restore`: mảng thô `[...]` → **`{ids:[...]}`**.
  - `categories/bulk-delete`, `categories/bulk-restore`: mảng thô `[...]` → **`{ids:[...]}`** (ids kiểu number).
  - Ngoại lệ: `PATCH /banners/reorder` là thao tác sắp thứ tự, không phải bulk CRUD; body vẫn là mảng ID có thứ tự, ví dụ `[3,1,2]`.

### A6. Tải file ✅ — BE ĐÃ FIX (BE-8)
- `GET /resources/{id}/file` giờ **stream đúng 200** (trước 500 do lazy-init + return type). Lỗi trả JSON: `404` (6001) không thấy, `403` (6004) không đủ quyền, `415`/`400` cho YouTube, `8002` STORAGE_ERROR nếu MinIO lỗi, `6009` (429) nếu vượt rate-limit. FE bật nút Tải/Xem + xử lý các mã này.

### A7. Pagination/sort đã chuẩn hóa ✅🔴
- **BE đã mở:** mọi endpoint list dùng `?page=&size=&sort=field,dir`; hỗ trợ lặp nhiều `sort`, cap `size` về 100.
- Field sort ngoài whitelist trả `400 / 9001`, không còn rơi xuống lỗi JPA/500.
- FE bỏ hoàn toàn `sortBy` + `sortDir` và workaround `sortBy=desc`.
- `GET /api/v1/banners` và `GET /api/v1/age-groups` giờ cũng trả `result: Page<DTO>` thay vì mảng trực tiếp; FE đọc dữ liệu tại `result.content`.

## B. NỢ FE mà review đã chỉ ra (FE tự sửa, không phụ thuộc BE deploy)

### B1. Bỏ workaround gửi `desc` vào `sortBy` 🔴 ([API-4])
- FE đang gửi sai `sortBy=desc`. Phải đổi sang `sort=id,desc` hoặc field hợp lệ khác. BE đã gỡ đoạn vá ở `CategoryController`; request cũ không còn được hỗ trợ.

### B2. Dọn `result ?? data` 🟡
- Mọi service đang phòng thủ cả `result` lẫn `data`. BE chốt **chỉ `result`** → dọn về một nhánh `response.result` cho gọn.

---

## C. Trạng thái phần còn lại của contract

> Cập nhật: **C1–C5 BE đã làm** (✅ — FE sync ngay).

### C1. Pagination/sort chuẩn ✅🔴 — BE ĐÃ ĐỔI
- Mọi list dùng `?page=&size=&sort=field,dir` (bỏ `sortBy`+`sortDir` rời rạc; whitelist field).
- FE đổi helper query ngay, ví dụ `sort=createdAt,desc`; có thể gửi nhiều tham số `sort`.
- `size > 100` được cap về 100; sort field không hợp lệ trả `400 / 9001`.

### C2. Method chuẩn (D2) ✅🔴 — BE ĐÃ ĐỔI
- **BE đã đổi verb** (sửa `resource/banner/category/user.service` theo đây):
  - `restore` resource & user: `PUT` → **`PATCH`** (`/resources/{id}/restore`, `/users/{id}/restore`).
  - Banner toggle: `PUT` → **`PATCH`** (`/banners/{id}/toggle`).
  - Tăng view: `PUT` → **`POST`** (`/resources/{id}/view`).
  - Xóa hàng loạt resource & category: `DELETE …/bulk` → **`POST …/bulk-delete`** (giữ body `ids`).
  - Giữ nguyên: favorite (POST), visibility (PATCH), approve/reject (PATCH), bulk-restore (PATCH).
- **Kèm fix:** trước đây `POST /resources/**` yêu cầu ADMIN/TEACHER nên **USER không favorite được** — BE đã cho `POST /resources/*/favorite` ở mức `authenticated()`. FE: USER giờ favorite được.

### C3. Download non-public cho owner/admin + rate-limit ✅🔴 — BE ĐÃ LÀM
- **BE đã mở:** owner hoặc ADMIN tải được file `PENDING/PRIVATE`; người khác/ẩn danh → **`6004` (403)**. Vượt rate-limit tải (IP+resource, 30 lần/60s) → **`6009` (429)** kèm `Retry-After`.
- **FE làm:** bật nút Tải/Xem ở **Teacher My Resources** & **Admin preview drawer** (gửi kèm cookie); xử lý **403 (6004)** và **429 (6009)**.

### C4. Policy mật khẩu chung (D3) ✅🔴 — BE ĐÃ ÁP
- **BE đã áp** cho register/reset/change: `≥8 ký tự + chữ hoa + chữ thường + số`, regex dùng chung:
  ```
  ^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$
  ```
  Không đạt → `1002` (form) hoặc `1013`. Message key `validation.password.weak`.
- **FE làm:** áp **cùng regex** ở các form tạo/đổi/reset mật khẩu: `register/settings/profile/reset-password`. Form login chỉ cần kiểm tra không rỗng vì mật khẩu cũ vẫn phải đăng nhập được.

### C5. Download lỗi luôn JSON ✅🟡
- Các lỗi nghiệp vụ được phát hiện trước khi bắt đầu stream trả `ApiResponse` JSON; FE đọc `code`/`message` thay vì xử lý như Blob thành công.
- Gián đoạn mạng xảy ra sau khi stream đã bắt đầu là lỗi transport và không thể đổi response đã gửi thành JSON.

---

## D. Bảng mã lỗi FE switch (đã freeze — xem §4 contract)

FE giữ switch theo `code` (không đổi số). Lưu ý 2 mã mới cần xử lý:
- **`7004` CONCURRENT_MODIFICATION** (409) — xung đột sửa đồng thời resource ⟶ "tải lại & thử lại".
- **`6009` RESOURCE_VIEW_RATE_LIMIT_EXCEEDED** (429) — giờ áp cho cả **download** (trước chỉ view) ⟶ đọc `Retry-After`.

---

## E. Thứ tự đề xuất cho FE

1. **Ngay:** sửa comment body, `visibility`, pagination/sort, HTTP verb và bulk payload trong các service.
2. Chuyển Banner/AgeGroup list sang đọc `result.content`; dọn `result ?? data`.
3. Bật nút tải/xem non-public + xử lý 403/429 và thêm xử lý `7004`.
4. Đồng bộ validator mật khẩu ở các form tạo/đổi/reset mật khẩu.

## F. Content-Type chính xác

- Category create: `multipart/form-data`; update: hỗ trợ JSON hoặc multipart khi thay icon.
- Topic create/update: `application/json`.
- Banner create/update: `multipart/form-data`.
- Resource create: `multipart/form-data`; update: hỗ trợ JSON hoặc multipart.
