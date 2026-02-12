# 📚 Tài liệu Tích hợp API Resource (Final Version - Verified with Code)

**Base URL:** `/api/v1/resources`
**Cấu trúc Response Chung:**
```json
{
  "code": 1000,
  "message": "Success message",
  "result": { ... }, // Dữ liệu chính
  "timestamp": "2023-12-01T10:00:00"
}
```

---

## 1. Quản lý Tài liệu (CRUD)

### A. Tạo mới (Upload)
*   **Endpoint:** `POST /api/v1/resources`
*   **Content-Type:** `multipart/form-data`

**Request Body (FormData):**
*   `file`: (File) hoặc `youtubeLink`: (String)
*   `title`: "Học đếm số"
*   `topicId`: 1
*   `thumbnail`: (File - Optional)
*   `description`: "Mô tả..."
*   `ageGroupIds`: 1,2 (Gửi lặp lại key hoặc mảng)

**Response (201 Created):**
```json
{
  "code": 1000,
  "message": "Resource uploaded successfully",
  "result": {
    "id": "a1b2c3d4-e5f6-...",
    "title": "Học đếm số",
    "slug": "hoc-dem-so-170123456",
    "description": "Video dạy bé đếm từ 1 đến 10",
    "resourceType": "YOUTUBE", // FILE hoặc YOUTUBE
    "fileUrl": "https://www.youtube.com/watch?v=xyz...",
    "thumbnailUrl": "https://img.youtube.com/vi/xyz/hqdefault.jpg",
    "fileType": "VIDEO", // VIDEO, DOCUMENT, PDF, EXCEL...
    "fileExtension": "youtube",
    "fileSize": 0,
    "status": "PENDING", // PENDING, APPROVED, REJECTED, HIDDEN
    "viewsCount": 0,
    "downloadCount": 0,
    "averageRating": 0.0,
    "isFavorited": false,
    "topic": {
      "id": 1,
      "name": "Toán học",
      "slug": "toan-hoc",
      "resourceCount": 10,
      "isActive": true
    },
    "ageGroups": [
      { "id": 1, "name": "3-4 Tuổi", "slug": "3-4-tuoi" }
    ],
    "createdAt": "2023-12-01T10:00:00",
    "updatedAt": "2023-12-01T10:00:00",
    "createdBy": "Nguyễn Văn A", // Full Name của người tạo
    "updatedBy": null
  }
}
```

### B. Cập nhật Thông tin (Update Info)
*   **Endpoint:** `PUT /api/v1/resources/{id}`
*   **Content-Type:** `application/json`

**Request Body:**
```json
{
  "title": "Học đếm số (Cập nhật)",
  "description": "Mô tả mới...",
  "topicId": 2,
  "status": "APPROVED",
  "youtubeLink": "https://youtu.be/new-link",
  "ageGroupIds": [1, 3]
}
```

### C. Cập nhật Ảnh bìa (Update Thumbnail)
*   **Endpoint:** `POST /api/v1/resources/{id}/thumbnail`
*   **Content-Type:** `multipart/form-data`

**Request Body:** `thumbnail` (File).

**Response:**
```json
{
  "code": 1000,
  "message": "Thumbnail updated successfully",
  "result": {
    "thumbnailUrl": "http://minio-server/resources/thumbnails/new-image.jpg"
  }
}
```

### D. Xóa mềm & Khôi phục
*   **Xóa:** `DELETE /api/v1/resources/{id}`
*   **Khôi phục:** `PUT /api/v1/resources/{id}/restore`

---

## 2. Danh sách & Bộ lọc (List & Filter)

*   **Endpoint:** `GET /api/v1/resources`
*   **Params:**
    *   `page`: 0 (Mặc định)
    *   `size`: 10 (Mặc định)
    *   `mine`: `true` (Lọc bài của tôi)
    *   `status`: `DELETED` (Thùng rác), `PENDING` (Chờ duyệt)...
    *   `keyword`: Tìm kiếm
    *   `topicId`, `categoryId`, `ageGroupId`: Lọc danh mục

**Response (Page<ResourceResponse>):**
```json
{
  "code": 1000,
  "message": "Resource list retrieved successfully",
  "result": {
    "content": [
      {
        "id": "uuid-1",
        "title": "Bài giảng 1",
        "thumbnailUrl": "...",
        "viewsCount": 100,
        "averageRating": 4.5,
        "isFavorited": true,
        "resourceType": "FILE",
        "fileType": "DOCUMENT",
        "createdAt": "2023-11-20T08:00:00",
        "createdBy": "Trần Thị B" // Full Name
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 10 },
    "totalPages": 5,
    "totalElements": 48,
    "last": false,
    "first": true,
    "empty": false
  }
}
```

---

## 3. Tương tác (Interaction)

### A. Xem chi tiết (Detail)
*   **Endpoint:** `GET /api/v1/resources/{slug}`
*   **Response:** Trả về `ResourceResponse` đầy đủ.

### B. Bình luận (Comment)
*   **Tạo:** `POST /api/v1/comments`
    *   Body: `{ "resourceId": "uuid", "content": "Hay quá", "rating": 5 }`
*   **Xem:** `GET /api/v1/comments?resourceId=uuid`

**Response (List Comment):**
```json
{
  "code": 1000,
  "result": {
    "content": [
      {
        "id": 10,
        "content": "Hay quá",
        "rating": 5,
        "username": "phuhuynh_a", // Username của người comment
        "userAvatar": "http://minio/avatar.jpg",
        "createdAt": "2023-12-01T10:05:00"
      }
    ],
    "totalPages": 1,
    "totalElements": 1
  }
}
```

### C. Yêu thích (Favorite)
*   **Endpoint:** `POST /api/v1/resources/{id}/favorite`

---

## 4. Checklist Logic UI

1.  **Hiển thị Icon/Player:**
    *   Dựa vào `resourceType`:
        *   `YOUTUBE` -> Iframe.
        *   `FILE` -> Check `fileType` (VIDEO -> Player, PDF -> Viewer, Khác -> Icon Download).
2.  **Nút "Khôi phục":** Chỉ hiện khi đang ở tab "Thùng rác" (`status=DELETED`).
3.  **Nút "Sửa":** Chỉ hiện khi `mine=true` hoặc user là Admin.
4.  **Rating:** Hiển thị sao trung bình từ `averageRating`.
5.  **CreatedBy:** Hiển thị tên người tạo (Full Name) thay vì username.
