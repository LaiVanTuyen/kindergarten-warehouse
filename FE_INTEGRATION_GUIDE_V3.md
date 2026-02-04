# 📚 Tài liệu Tích hợp API Resource V3 (Final Production Ready)

**Base URL:** `/api/v1/resources`
**Phiên bản:** V3 (Cập nhật hỗ trợ YouTube, Thumbnail, Resource Type)

---

## 1. Upload Tài liệu mới (Create)
Hỗ trợ 2 chế độ: Upload File hoặc Nhập Link YouTube.

*   **Endpoint:** `POST /api/v1/resources`
*   **Content-Type:** `multipart/form-data`

**Request Body (FormData):**

| Key | Type | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `title` | String | ✅ | Tên tiêu đề tài liệu. |
| `topicId` | Long | ✅ | ID của Chủ đề. |
| `description` | String | ❌ | Mô tả ngắn gọn. |
| `thumbnail` | File | ❌ | **Ảnh bìa tùy chỉnh**. Nếu không up, hệ thống sẽ tự lấy (nếu là YouTube) hoặc để trống. |
| `file` | File | ❌ | **File tài liệu** (.mp4, .pdf, .docx...). Bắt buộc nếu không có `youtubeLink`. |
| `youtubeLink` | String | ❌ | **Link YouTube**. Bắt buộc nếu không có `file`. |
| `ageGroupIds` | List | ❌ | Danh sách ID nhóm tuổi. |

**Logic UI (Frontend):**
*   Tạo 2 Tab hoặc Radio Button: **"Tải file lên"** và **"Link YouTube"**.
*   Nếu chọn "Tải file": Hiện nút chọn file, ẩn ô nhập link.
*   Nếu chọn "YouTube": Hiện ô nhập link, ẩn nút chọn file.
*   Ô "Ảnh bìa" (Thumbnail) luôn hiển thị (Optional).

---

## 2. Cập nhật Tài liệu (Update Info)
Dùng để sửa thông tin text (Tên, Mô tả, Link YouTube...).

*   **Endpoint:** `PUT /api/v1/resources/{id}`
*   **Content-Type:** `application/json` (JSON Body)

**Request Body (JSON):**
```json
{
  "title": "Tên mới",
  "description": "Mô tả mới",
  "topicId": 5,
  "ageGroupIds": [1, 2],
  "status": "APPROVED",
  "youtubeLink": "https://youtu.be/..." // Nếu muốn đổi link video
}
```

---

## 3. Cập nhật Ảnh bìa (Update Thumbnail) - MỚI
Dùng riêng để đổi ảnh bìa (vì API Update ở trên dùng JSON không gửi file được).

*   **Endpoint:** `POST /api/v1/resources/{id}/thumbnail`
*   **Content-Type:** `multipart/form-data`

**Request Body (FormData):**
*   `thumbnail`: File ảnh mới (Required).

**Response:**
```json
{
  "code": 1000,
  "message": "Thumbnail updated successfully",
  "result": {
    "thumbnailUrl": "http://minio/..."
  }
}
```

---

## 4. Hiển thị Chi tiết (View Detail)
Dựa vào `resourceType` để render trình phát phù hợp.

**Response Data:**
```json
{
  "id": "uuid",
  "title": "Học đếm số",
  "resourceType": "YOUTUBE", // hoặc "FILE"
  "fileUrl": "https://www.youtube.com/watch?v=abc...",
  "fileType": "VIDEO",
  "thumbnailUrl": "https://img.youtube.com/..."
}
```

**Logic Render (Frontend):**

1.  **Kiểm tra `resourceType`:**
    *   Nếu `YOUTUBE`:
        *   Lấy ID từ `fileUrl` (hoặc dùng thư viện embed).
        *   Render `<iframe>` hoặc `ngx-youtube-player`.
    *   Nếu `FILE`:
        *   Kiểm tra `fileType`.
        *   `VIDEO`: Render thẻ `<video src="fileUrl">`.
        *   `PDF`: Render PDF Viewer.
        *   `DOCUMENT/EXCEL`: Hiển thị nút "Tải về để xem" hoặc tích hợp Google Docs Viewer.

2.  **Thumbnail:**
    *   Luôn ưu tiên hiển thị `thumbnailUrl` nếu có.
    *   Nếu `thumbnailUrl` null -> Hiển thị Icon mặc định theo `fileType`.

---

## 5. Các tính năng khác (Giữ nguyên V2)
*   **Lọc bài của tôi:** `GET /resources?mine=true`
*   **Thùng rác:** `GET /resources?status=DELETED`
*   **Khôi phục:** `PUT /resources/{id}/restore`
*   **Bình luận:** `POST /api/v1/comments`
*   **Yêu thích:** `POST /api/v1/resources/{id}/favorite`

---

## 📝 Checklist cho FE Developer
1.  [ ] **Form Upload:** Cập nhật form để hỗ trợ nhập Link YouTube.
2.  [ ] **Detail Page:** Thêm logic render Iframe cho YouTube.
3.  [ ] **List Item:** Hiển thị thumbnail từ URL (nếu có) thay vì chỉ hiện icon.
4.  [ ] **Update:** Chuyển sang gửi JSON Body.
5.  [ ] **Change Thumbnail:** Thêm nút "Đổi ảnh bìa" riêng, gọi API `POST .../thumbnail`.
