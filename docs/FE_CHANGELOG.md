# BE Changelog cho FE — 2026-06-15

> Nhánh BE: `fix/senior-review-phase1`. Tài liệu này tóm tắt **mọi thay đổi BE ảnh hưởng FE** + việc FE cần làm.
> Chi tiết đầy đủ: [FE_MIGRATION_CHECKLIST.md](./FE_MIGRATION_CHECKLIST.md) · Hợp đồng API: [API_CONTRACT_V1.md](./API_CONTRACT_V1.md).
> 🔴 = breaking (FE phải đổi) · 🟢 = không phá, chỉ tận dụng.

---

## 1. 🔴 `visibility` thay cho `isActive` (Category/Topic/Banner)
- Bỏ field `isActive` (boolean) → dùng **`visibility: "PUBLIC" | "PRIVATE"`** (giống Resource).
- **FE:** đổi model + form tạo/sửa (gửi `visibility`), bộ lọc admin `ACTIVE/INACTIVE` → `PUBLIC/PRIVATE`. "Đang bật" ⟺ `visibility === 'PUBLIC'`.

## 2. 🔴 HTTP method chuẩn hóa
| Thao tác | Cũ | Mới |
|---|---|---|
| restore resource/user | `PUT` | **`PATCH`** |
| banner toggle | `PUT` | **`PATCH`** |
| block/unblock user | `PUT` | **`PATCH`** `/users/{id}/block` |
| tăng view | `PUT /{id}/view` | **`POST`** |
| xóa hàng loạt resource/category | `DELETE …/bulk` | **`POST …/bulk-delete`** |
- Giữ nguyên: favorite (POST, giờ **USER cũng dùng được**), visibility (PATCH), approve/reject (PATCH), bulk-restore (PATCH).

## 3. 🔴 Bulk payload thống nhất `{ids}`
- Tất cả bulk dùng body **`{ "ids": [...] }`** (bulk-reject thêm `reason`). `resourceIds`/mảng thô cũ → **400**.
  - admin `bulk-approve`/`bulk-reject`: `{resourceIds}` → `{ids}` (reject: `{ids, reason}`)
  - `resources/bulk-delete`, `resources/bulk-restore`: `[...]` → `{ids:[...]}`
  - `categories/bulk-delete`, `categories/bulk-restore`: `[...]` → `{ids:[...]}` (ids number)

## 4. 🔴 Password policy dùng chung
- register/reset/change: **≥8 ký tự + chữ hoa + thường + số**. Regex chung:
  `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$` → FE áp ở login/register/settings/profile/reset.

## 5. 🟢 Tải file (download) — đã sửa, dùng được
- `GET /resources/{id}/file`: stream **200**; owner/ADMIN tải được file PENDING/PRIVATE; lỗi luôn trả **JSON** (`404/6001`, `403/6004`, `8002` MinIO lỗi, `6009` rate-limit 429 kèm `Retry-After`).
- **FE:** bật nút Tải/Xem ở My Resources & Admin drawer; xử lý 403/429.

## 6. 🟢 Response & lỗi
- Envelope chỉ dùng **`result`** (bỏ `data`); list luôn `Page<DTO>`.
- AuditLog trả DTO (field ổn định: id, action, username, target, detail, ipAddress, userAgent, timestamp).
- Comment: `POST /comments` body `{resourceId, content, rating}`; `GET /comments?resourceId=&page&size&sort=createdAt,desc`.
- Pagination: `?page&size&sort=field,dir` (whitelist sort).

## 7. Mã lỗi mới cần xử lý (bảng đầy đủ ở API_CONTRACT_V1 §4)
| code | HTTP | Ý nghĩa |
|---|---|---|
| `7004` | 409 | Xung đột sửa đồng thời (optimistic lock) → "tải lại & thử lại" |
| `6009` | 429 | Vượt rate-limit view/**download** (đọc `Retry-After`) |
| `9002` | 405 | Sai HTTP method |
| `9003` | 415 | Sai Content-Type (vd gửi JSON vào endpoint chỉ nhận multipart) |
| `8002` | 500 | Lỗi storage (MinIO) khi tải file |

## 8. Ghi chú môi trường (dev)
- **CSRF:** BE đúng (raw double-submit). FE phải gắn `X-XSRF-TOKEN` cho request ghi — với cross-origin (`:4200`→`:8080`) dùng **dev proxy** hoặc interceptor thủ công (Angular không tự gắn header cho URL tuyệt đối khác origin).
- **SMTP** chưa cấu hình → OTP verify/reset **in ra log BE** (`[DEV-EMAIL]...code=`) để test. Mail thật cần điền creds + restart.
- `category/topic/banner` create/update là **multipart/form-data** (có upload ảnh) — gửi JSON sẽ nhận `415`.

---
*BE đã build + chạy + verify live tất cả thay đổi trên `localhost:8080`. FE chạy lại E2E rồi báo lệch (nếu có).*
