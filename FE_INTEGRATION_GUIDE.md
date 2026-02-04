# 📚 Tài liệu Tích hợp API Resource (Resource Module Integration)

**Base URL:** `/api/v1/resources`
**Authentication:**
*   🔒 **Yêu cầu Token:** Các API Upload, Update, Delete, Favorite.
*   🔓 **Public:** Các API Xem danh sách, Chi tiết, Tăng view/download.

---

## 1. Upload Tài liệu mới (Create)
Dùng để Admin hoặc Giáo viên tải lên tài liệu (Video, PDF, Word...).

*   **Endpoint:** `POST /api/v1/resources`
*   **Quyền:** `ADMIN`, `TEACHER`
*   **Content-Type:** `multipart/form-data` (Bắt buộc)

**Request Body (FormData):**

| Key | Type | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `file` | File | ✅ | File tài liệu (.mp4, .pdf, .docx, .xlsx...). |
| `title` | String | ✅ | Tên tiêu đề tài liệu. |
| `description` | String | ❌ | Mô tả ngắn gọn. |
| `topicId` | Long | ✅ | ID của Chủ đề (Topic). |
| `ageGroupIds` | List<Long> | ❌ | Danh sách ID nhóm tuổi. <br> *Lưu ý: Gửi dạng `ageGroupIds=1&ageGroupIds=2`* |

**Response (Success - 201 Created):**

```json
{
  "code": 1000,
  "message": "Upload resource successfully",
  "result": {
    "id": "uuid-string",
    "title": "Bài giảng toán lớp 1",
    "slug": "bai-giang-toan-lop-1-17000000",
    "fileUrl": "http://minio-host/resources/file.mp4",
    "fileType": "VIDEO",
    "status": "PENDING"
  }
}
```

---

## 2. Lấy Danh sách & Lọc (Get List & Filter)
Dùng cho trang danh sách, tìm kiếm.

*   **Endpoint:** `GET /api/v1/resources`
*   **Quyền:** Public (Nhưng nếu có Token sẽ trả về trạng thái `isFavorited` chính xác).

**Query Parameters:**

| Param | Type | Mô tả | Ví dụ |
| :--- | :--- | :--- | :--- |
| `page` | int | Số trang (bắt đầu từ 0). | `0` |
| `size` | int | Số lượng item/trang. | `10` |
| `keyword` | String | Tìm kiếm theo tên tài liệu. | `toán` |
| `topicId` | Long | Lọc theo ID chủ đề. | `5` |
| `categoryId` | Long | Lọc theo ID danh mục cha. | `2` |
| `ageGroupId` | Long | Lọc theo ID nhóm tuổi. | `1` |
| `status` | String | Trạng thái (User chỉ xem `APPROVED`). | `APPROVED`, `PENDING` |
| `sort` | String | Sắp xếp (mặc định `createdAt,desc`). | `viewsCount,desc` |

**Response (Success - 200 OK):**

```json
{
  "code": 1000,
  "message": "Get resources list successfully",
  "result": {
    "content": [
      {
        "id": "uuid-1",
        "title": "Video học hát",
        "thumbnailUrl": "...",
        "viewsCount": 150,
        "isFavorited": true,  // Quan trọng: User đã like bài này chưa
        "topic": { "id": 1, "name": "Âm nhạc" }
      }
    ],
    "totalPages": 5,
    "totalElements": 48,
    "size": 10,
    "number": 0
  }
}
```

---

## 3. Xem Chi tiết (Get Detail)
Dùng cho trang chi tiết tài liệu.

*   **Endpoint:** `GET /api/v1/resources/{slug}`
*   **Quyền:** Public
*   **Path Variable:** `slug` (Chuỗi định danh trên URL).

**Response (Success - 200 OK):**

```json
{
  "code": 1000,
  "result": {
    "id": "uuid-1",
    "title": "Video học hát",
    "description": "Bài hát cho bé...",
    "fileUrl": "Link file gốc (để play video hoặc tải)",
    "fileType": "VIDEO",
    "isFavorited": false,
    "ageGroups": [ { "id": 1, "name": "3-4 Tuổi" } ]
  }
}
```

---

## 4. Cập nhật Tài liệu (Update)
Dùng để sửa thông tin hoặc **Duyệt bài** (Admin).

*   **Endpoint:** `PUT /api/v1/resources/{id}`
*   **Quyền:** `ADMIN`, `TEACHER`
*   **Content-Type:** Query Params (Không gửi body JSON).

**Query Parameters:**

| Param | Type | Mô tả |
| :--- | :--- | :--- |
| `title` | String | Tên mới (Optional). |
| `description` | String | Mô tả mới (Optional). |
| `topicId` | Long | ID chủ đề mới (Optional). |
| `ageGroupIds` | List | Danh sách ID nhóm tuổi mới (Optional). |
| `status` | Enum | **Dùng để duyệt bài**: `APPROVED`, `REJECTED`, `HIDDEN`. |

**Ví dụ URL:**
`/api/v1/resources/uuid-123?title=NewTitle&status=APPROVED`

---

## 5. Yêu thích / Bỏ yêu thích (Toggle Favorite)
User bấm nút trái tim.

*   **Endpoint:** `POST /api/v1/resources/{id}/favorite`
*   **Quyền:** `Authenticated User`
*   **Logic:** Nếu chưa like -> Thêm like. Nếu đã like -> Bỏ like.

**Response:**

```json
{
  "code": 1000,
  "message": "Favorite status toggled",
  "result": null
}
```

---

## 6. Tương tác (View & Download)

### A. Tăng lượt xem (View)
*   **Endpoint:** `PUT /api/v1/resources/{id}/view`
*   **Khi nào gọi:** Khi user mở trang chi tiết hoặc bấm Play video.
*   **Lưu ý:** Server có cache 1 giờ/1 IP. FE cứ gọi mỗi khi mở trang, Server tự lọc spam.

### B. Tăng lượt tải (Download)
*   **Endpoint:** `PUT /api/v1/resources/{id}/download`
*   **Khi nào gọi:** Khi user bấm nút "Tải xuống".

---

## 7. Xóa Tài liệu (Delete)
*   **Endpoint:** `DELETE /api/v1/resources/{id}`
*   **Quyền:** `ADMIN`, `TEACHER` (Chỉ xóa bài mình tạo).
*   **Cơ chế:** Soft Delete (Chuyển vào thùng rác).

---

## 📝 Các Enum cần Map (Frontend Constants)

**1. ResourceStatus (Trạng thái)**
| Enum | Ý nghĩa | Màu sắc gợi ý (Badge) |
| :--- | :--- | :--- |
| `PENDING` | Chờ duyệt | 🟡 Vàng (Warning) |
| `APPROVED` | Đã duyệt | 🟢 Xanh lá (Success) |
| `REJECTED` | Từ chối | 🔴 Đỏ (Danger) |
| `HIDDEN` | Đã ẩn | ⚫ Xám (Secondary) |

**2. FileType (Loại file)**
| Enum | Ý nghĩa | Icon gợi ý |
| :--- | :--- | :--- |
| `VIDEO` | Video (.mp4, .mov) | 🎥 Play Icon |
| `DOCUMENT` | Word (.doc, .docx) | 📝 Word Icon |
| `EXCEL` | Excel (.xls, .xlsx) | 📊 Excel Icon |
| `PDF` | PDF (.pdf) | 📕 PDF Icon |

---

### 💡 Lưu ý cho Frontend Developer:
1.  **Upload File:** Nhớ hiển thị loading/progress bar vì upload video có thể mất thời gian.
2.  **Favorite:** Khi render danh sách, check `item.isFavorited` để tô màu trái tim đỏ/xám ngay lập tức.
3.  **Filter:** Khi Admin duyệt bài, gọi API Update với `status=APPROVED`.
