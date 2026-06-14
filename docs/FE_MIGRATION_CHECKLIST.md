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
- **BE đã chuẩn hóa** TẤT CẢ bulk về body `{ "ids": [...] }` (bulk-reject thêm `reason`), đúng contract §2.5. Mảng thô / `resourceIds` cũ giờ **bị 400**.
- **FE làm (đổi lại):**
  - admin `bulk-approve`/`bulk-reject`: `{resourceIds}` → **`{ids}`** (reject: `{ids, reason}`).
  - `resources/bulk-delete`, `resources/bulk-restore`: mảng thô `[...]` → **`{ids:[...]}`**.
  - `categories/bulk-delete`, `categories/bulk-restore`: mảng thô `[...]` → **`{ids:[...]}`** (ids kiểu number).

### A6. Tải file ✅ — BE ĐÃ FIX (BE-8)
- `GET /resources/{id}/file` giờ **stream đúng 200** (trước 500 do lazy-init + return type). Lỗi trả JSON: `404` (6001) không thấy, `403` (6004) không đủ quyền, `415`/`400` cho YouTube, `8002` STORAGE_ERROR nếu MinIO lỗi, `6009` (429) nếu vượt rate-limit. FE bật nút Tải/Xem + xử lý các mã này.

## B. NỢ FE mà review đã chỉ ra (FE tự sửa, không phụ thuộc BE deploy)

### B1. Bỏ workaround gửi `desc` vào `sortBy` 🔴 ([API-4])
- FE đang gửi sai `sortBy=desc`. Phải sửa thành quy ước chuẩn (xem C1). BE sẽ gỡ đoạn vá ở `CategoryController` khi FE xong.

### B2. Dọn `result ?? data` 🟡
- Mọi service đang phòng thủ cả `result` lẫn `data`. BE chốt **chỉ `result`** → dọn về một nhánh `response.result` cho gọn.

---

## C. Trạng thái phần còn lại của contract

> Cập nhật: **C2 (method), C3 (download non-public), C4 (password) BE đã làm** (✅ — FE sync ngay).
> Chỉ còn **C1 (pagination)** là BE chưa triển khai — FE chưa cần sửa, đừng hard-code bám hành vi cũ.

### C1. Pagination/sort chuẩn ⏳🔴
- Đích: mọi list dùng `?page=&size=&sort=field,dir` (bỏ `sortBy`+`sortDir` rời rạc; whitelist field).
- FE: chuẩn bị helper build query `sort=createdAt,desc`; chưa đổi cho tới khi BE mở.

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
- **FE làm:** áp **cùng regex** ở `login/register/settings/profile/reset-password`. (Register cũ cho ≥6 → BE đã nâng ≥8; FE phải nâng theo nếu không sẽ bị BE chặn.)

### C5. Download lỗi luôn JSON ⏳🟡
- Đích: khi download lỗi, BE trả `ApiResponse` JSON (không nhúng lỗi vào stream). FE đọc JSON lấy `code`/`message`. (BE phần lớn đã JSON; sẽ xác nhận khi làm C3.)

---

## D. Bảng mã lỗi FE switch (đã freeze — xem §4 contract)

FE giữ switch theo `code` (không đổi số). Lưu ý 2 mã mới cần xử lý:
- **`7004` CONCURRENT_MODIFICATION** (409) — xung đột sửa đồng thời resource ⟶ "tải lại & thử lại".
- **`6009` RESOURCE_VIEW_RATE_LIMIT_EXCEEDED** (429) — giờ áp cho cả **download** (trước chỉ view) ⟶ đọc `Retry-After`.

---

## E. Thứ tự đề xuất cho FE

1. **Ngay (BE đã deploy A1–A3):** sửa `comment.service` (A1), model+filter `visibility` (A3), thêm xử lý `7004` (A4/D). Tự dọn B1, B2.
2. **Khi BE báo C1/C2:** đổi pagination + verb (sửa các service một lượt).
3. **Khi BE báo C3:** bật nút tải/xem non-public + xử lý 403/429.
4. **Cùng đợt với BE C4:** đồng bộ validator mật khẩu.
