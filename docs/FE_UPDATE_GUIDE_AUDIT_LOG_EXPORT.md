# Hướng Dẫn Tích Hợp FE: API Export CSV Cho Audit Logs

## Tổng Quan
Module Audit Logs đã được Backend nâng cấp để hỗ trợ xuất file CSV (Download) an toàn với dung lượng lớn. Thay vì trả về một cục mảng byte JSON dễ gây sập RAM trình duyệt và treo Server, Backend sử dụng cơ chế **Streaming (Luồng I/O liên tục)** và **Time-Bounding (Chốt Mốc Thời Gian)**.

Frontend cần nắm rõ cách thức gọi API Download này để xử lý ghi file về máy người dùng một cách mượt mà nhất.

---

## API Documentation

### 1. Endpoint Tải Xuống (CSV Export)
*   **URL:** `GET /api/v1/audit-logs/export`
*   **Quyền hạn:** `ADMIN` (yêu cầu gửi Token Bearer)
*   **Tham Số (Query Parameters):** API Export này TÁI SỬ DỤNG y hệt toàn bộ lưới tham số của API Lấy danh sách bình thường (`GET /api/v1/audit-logs`).
    *   `action`: Chọn các hành động (VD: `LOGIN`, `APPROVE_BULK`, `DELETE_BULK`).
    *   `target`: Lọc theo đối tượng.
    *   `username`: Tìm theo tên tài khoản.
    *   `startDate` / `endDate`: Lọc theo khoảng thời gian (`yyyy-MM-dd`).
    *   `sortBy` / `sortDir`: Sắp xếp.

**Lưu Ý:** KHÔNG CẦN gửi tham số `page` và `size`, API Export sẽ tự động gom toàn bộ dữ liệu thỏa mãn bộ lọc (hàng chục ngàn dòng) để tuồn thành luồng (Stream) đẩy xuống trình duyệt.

---

## 2. Hướng Dẫn Kỹ Thuật Cho Frontend (Angular / React / Vue)

Vì API trả về dữ liệu kiểu dạng Stream (File Content) chứ không phải JSON thuần, Frontend **TUYỆT ĐỐI KHÔNG** được parse bằng `.json()`.

Frontend Angular (ví dụ dùng `HttpClient`) phải set thuộc tính `responseType: 'blob'`.

### Ví dụ code Angular xử lý tải file:

```typescript
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  constructor(private http: HttpClient) {}

  exportAuditLogsAsCsv(filterParams: any) {
    // 1. Dựng HttpParams y hệt như lúc tìm kiếm data View
    let params = new HttpParams();
    if (filterParams.action) params = params.set('action', filterParams.action);
    if (filterParams.username) params = params.set('username', filterParams.username);
    // ... vân vân 

    // 2. Bắt buộc set responseType: 'blob'
    return this.http.get('/api/v1/audit-logs/export', {
      params: params,
      responseType: 'blob', // QUAN TRỌNG NHẤT
      observe: 'response'   // Lấy response tống quan để đọc Headers (filename)
    }).subscribe({
      next: (response) => {
        // 3. Xử lý tải xuống máy người dùng (Tạo siêu liên kết ảo)
        const blob = new Blob([response.body], { type: 'text/csv; charset=utf-8' });
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadUrl;
        
        // 4. Lấy Tên file từ Backend gửi lên, hoặc tự đặt
        link.download = `Audit_Logs_${new Date().getTime()}.csv`;
        
        // Kích hoạt click để tải
        document.body.appendChild(link);
        link.click();
        
        // Dọn rác
        document.body.removeChild(link);
        window.URL.revokeObjectURL(downloadUrl);
      },
      error: (err) => {
        console.error("Lỗi khi export CSV:", err);
        // Hiển thị Toast báo lỗi cho Admin
      }
    });
  }
}
```

---

## 3. Kiến Trúc Backend (FE Cần Biết Để Yên Tâm)
1.  **UTF-8 BOM:** Backend đã tự nhúng mã đánh dấu `BOM` vào file CSV. Khi Admin tải file về và nhấn đúp mở trực tiếp bằng Microsoft Excel, tiếng Việt có dấu sẽ hiển thị chuẩn xác 100%, không bị vỡ font chữ hỏng layout.
2.  **Streaming & Zero Drift:** Ngay khi FE bấm "Tải xuống", Backend lập tức chốt 1 mốc thời gian của giây đó (Snapshot Time) và bắt đầu bơm dữ liệu trả về theo từng cụm. Việc này có nghĩa là, kể cả khi file rất lớn và mất 5-10 giây để tải qua mạng:
    *   Trình duyệt FE sẽ không bị treo.
    *   Báo cáo xuất ra chuẩn thời điểm bấm nút 100%, không bị các thao tác Log mới từ hệ thống làm xen ngang / gây trùng lặp dòng trong file CSV.
