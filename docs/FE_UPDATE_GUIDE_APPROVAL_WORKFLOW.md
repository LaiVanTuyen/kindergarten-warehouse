# Hướng Dẫn Nâng Cấp FE: Luồng Duyệt Bài (Approve & Reject Workflow)

## Tổng Quan
Để hệ thống chuyên nghiệp và bảo mật hơn, Backend đã gỡ bỏ hoàn toàn việc cho phép user phổ thông thay đổi trạng thái `status` tự do qua API update cũ. Đồng thời, bổ sung luồng **Lý do từ chối (Rejection Reason)** và tự động gửi duyệt lại (Auto-Pending).

FE Team cần update lại Model, UI và gọi các API mới theo hướng dẫn dưới đây.

---

## 1. Cập Nhật Model `ResourceResponse`
Tất cả các API trả về danh sách tài liệu (`GET /resources`, `GET /resources/me`, `GET /admin/resources`) và API lấy chi tiết (`GET /resources/{slug}`) đều trả thêm một field mới.

**Mới:** Thêm field `rejectionReason` (kiểu chuỗi hoặc null) vào Model.
```typescript
export interface ResourceResponse {
  // ... các field cũ ...
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null; // <--- FIELD MỚI (Lý do admin từ chối)
}
```

---

## 2. Thay Đổi UI: Màn Hình Của Học Sinh/Giáo Viên (Của Tôi)

### Khung Cảnh (Bị Từ Chối)
Khi User vào trang quản lý "Tài liệu của tôi" (`GET /resources/me`) hoặc xem chi tiết tài liệu do mình up lên.
- **Nếu `status === 'REJECTED'`**: Yêu cầu FE render một cái Warning Alert / Info Box màu đỏ/cam báo động.
- In ra nội dung của field `rejectionReason` để user biết phải sửa cái gì.
  - *Ví dụ Ui:* ⚠️ **Tài liệu bị từ chối:** `{rejectionReason}`. Vui lòng nhấn nút "Chỉnh sửa" để cập nhật lại file.

### Khung Cảnh (Gửi Duyệt Lại - Auto Pending)
- Khi user bấm **Chỉnh Sửa (Edit/Cập nhật API `PUT /resources/{id}`)**:
  - FE **KHÔNG CẦN** gửi trường `status` lên Backend nữa.
  - Sau khi gọi API `PUT` update nội dung (đổi title, up file mới) thành công -> Backend sẽ tự động đá status của tài liệu đó về `PENDING` và xóa sạch `rejectionReason`.
  - Lúc này màn UI (Của tôi) sẽ đổi Badge file này từ Đỏ (REJECTED) -> Vàng (PENDING - Chờ duyệt). FE lưu ý fetch/sync lại list cho chuẩn màu sắc.

---

## 3. Thay Đổi UI: Màn Hình Admin CMS

### API Duyệt Bài (Approve)
Khi Admin muốn duyệt cho một bài lên sóng (`APPROVED`). **Tuyệt đối không dùng API `PUT /resources/{id}` nữa.**
- **Hành động UI**: Admin bấm nút [Duyệt] ở dạng list hoặc trang chi tiết.
- Gọi **API MỚI**: 
  - `PATCH /api/v1/admin/resources/{id}/approve`
  - Không cần gửi body.

### API Từ Chối (Reject)
Khi Admin thấy bài rác, lỗi bản quyền, sai file...
- **Hành động UI**: Admin bấm nút [Từ chối]. Frontend cẩn popup một cái Modal nhỏ (Dialog) yêu cầu Admin nhập lý do (Textarea).
- Gọi **API MỚI**: 
  - `PATCH /api/v1/admin/resources/{id}/reject`
  - Body: `{ "reason": "Nội dung lý do admin nhập vào ô text... (bắt buộc, max 1000 ký tự)" }`
- Nút bấm sẽ gọi API này, Backend tự đá status thành `REJECTED` và ghim lỗi vào DB.

---

## 4. Tóm Lược API Calls (Quick Ref)

| Hành Động | Who | Định Tuyến / Method | Ghi Chú |
| :--- | :--- | :--- | :--- |
| Xem ds công khai | Guest | `GET /api/v1/resources` | Trả về mặc định toàn bộ Public & Approved. |
| Xem ds của tôi | Uploader | `GET /api/v1/resources/me` | Truyền token. Trả toàn bộ của chính mình. |
| Xem toàn DB | Admin | `GET /api/v1/admin/resources` | Lấy mọi record. Có thể filter bằng tham số `?status=...` |
| Cập nhật bài lỗi | Uploader | `PUT /api/v1/resources/{id}` | FE chỉ gửi tiêu đề/file. Backend tự đá trạng thái về PENDING. |
| Duyệt bài | Admin | `PATCH /api/v1/admin/resources/{id}/approve` | Thao tác 1 nút bấm (One-click) |
| Chặn bài / Từ chối | Admin | `PATCH /api/v1/admin/resources/{id}/reject` | Mở Modal -> Nhập lý do (Body: `{ "reason": "..." }`) |
| Bulk Approve | Admin | `PATCH /api/v1/admin/resources/bulk-approve` | Body: `{ "resourceIds": ["id1", "id2"] }` |
| Bulk Reject | Admin | `PATCH /api/v1/admin/resources/bulk-reject` | Body: `{ "resourceIds": ["id1"], "reason": "Lý do chung" }` |

---

## 5. Cập Nhật Mới: API Phê Duyệt/Từ Chối Hàng Loạt (Bulk Actions)
Để giải quyết bài toán hiệu năng/quá tải khi FE gọi vòng lặp duyệt từng bài, Backend cung cấp 2 Endpoint chuyên dụng cho việc duyệt theo Lô (Batch Processing):

- **Approve Hàng Loạt:** `PATCH /api/v1/admin/resources/bulk-approve`
  - Body Payload: `{ "resourceIds": ["id1", "id2", "id3"] }`
- **Reject Hàng Loạt:** `PATCH /api/v1/admin/resources/bulk-reject`
  - Body Payload: `{ "resourceIds": ["id1", "id2", "id3"], "reason": "Lý do chung" }`

**Lưu ý cực kỳ quan trọng về Response trả về:**
```json
{
  "status": "SUCCESS",
  "message": "Bulk reject operation completed.",
  "data": {
    "successCount": 2,
    "failedIds": ["id3"], // <--- BẮT BUỘC CHECK FIELD NÀY
    "message": "Bulk reject operation completed."
  }
}
```
**Frontend Xử Lý Logics:** Lỗi sẽ không làm sập cả cụm Batch. FE phải đọc mảng `failedIds`. Những file nào nằm trong `failedIds` chứng tỏ chưa duyệt/từ chối thành công -> Cần hiển thị **bôi đỏ** trên Grid hoặc thông báo nhẹ để Admin biết. Những ID không nằm trong list này là đã thành công.

---

## 6. Cập Nhật Mới: Lọc Audit Log Bằng Action Enum Mới
Nếu Frontend đang xây dựng thanh Dropdown (Filter) để lọc Lịch Sử Hoạt Động (`GET /api/v1/audit-logs?action=...`), vui lòng bổ sung 4 Enum mới sau đây vào danh sách lựa chọn của Dropdown:

- `APPROVE_BULK` (Duyệt nhiều file)
- `REJECT_BULK` (Từ chối nhiều file)
- `DELETE_BULK` (Xóa nhiều file / Xóa vào thùng rác)
- `RESTORE_BULK` (Khôi phục nhiều file)

⚠️ Nếu gọi Filter với action gõ sai chữ hoặc gửi các chuỗi tự sáng tạo (ví dụ `REJECT_ALL`), Backend Spring Boot sẽ ném lỗi HTTP 400 Bad Request ngay lập tức do không khớp `AuditAction` Enum.
