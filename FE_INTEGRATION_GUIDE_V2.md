# 📚 Tài liệu Tích hợp API Resource V2 (Resource Module Integration)

**Base URL:** `/api/v1/resources`
**Authentication:**
*   🔒 **Yêu cầu Token:** Các API Upload, Update, Delete, Restore, Favorite, Comment.
*   🔓 **Public:** Các API Xem danh sách, Chi tiết, Tăng view/download, Xem comment.

---

## 1. Upload Tài liệu mới (Create)
*   **Endpoint:** `POST /api/v1/resources`
*   **Quyền:** `ADMIN`, `TEACHER`
*   **Content-Type:** `multipart/form-data`

**Request Body (FormData):**
*   `file`: File tài liệu (Required).
*   `title`: Tên tiêu đề (Required).
*   `description`: Mô tả (Optional).
*   `topicId`: ID Chủ đề (Required).
*   `ageGroupIds`: Danh sách ID nhóm tuổi (Optional).

---

## 2. Lấy Danh sách & Lọc (Get List & Filter)
*   **Endpoint:** `GET /api/v1/resources`
*   **Quyền:** Public

**Query Parameters Mới (V2):**
*   `mine`: `true` (Lọc các bài do chính user đang login tạo - Dành cho Giáo viên quản lý bài của mình).
*   `status`: `DELETED` (Xem thùng rác - Dành cho Admin).
*   `createdBy`: (Backend dùng nội bộ, FE dùng `mine=true` tiện hơn).

**Ví dụ:**
*   Lấy bài của tôi: `GET /resources?mine=true`
*   Lấy bài đã xóa: `GET /resources?status=DELETED`

---

## 3. Cập nhật & Duyệt bài (Update)
*   **Endpoint:** `PUT /api/v1/resources/{id}`
*   **Quyền:** `ADMIN`, `TEACHER`
*   **Params:** `title`, `description`, `topicId`, `ageGroupIds`, `status`.

**Duyệt bài:** Admin gọi API này với `status=APPROVED`.

---

## 4. Xóa & Khôi phục (Delete & Restore)
*   **Xóa (Soft Delete):** `DELETE /api/v1/resources/{id}`
*   **Khôi phục (Restore):** `PUT /api/v1/resources/{id}/restore` (Mới V2)

---

## 5. Bình luận & Đánh giá (Comment & Rating) - MỚI V2

### A. Viết Bình luận (Create Comment)
*   **Endpoint:** `POST /api/v1/comments`
*   **Quyền:** `Authenticated User`
*   **Params:**
    *   `resourceId`: ID tài liệu.
    *   `content`: Nội dung bình luận.
    *   `rating`: Số sao (1-5).

### B. Xem Bình luận (Get Comments)
*   **Endpoint:** `GET /api/v1/comments`
*   **Quyền:** Public
*   **Params:** `resourceId`, `page`, `size`.

### C. Xóa Bình luận (Delete Comment)
*   **Endpoint:** `DELETE /api/v1/comments/{id}`
*   **Quyền:** Chủ comment hoặc Admin.

---

## 6. Yêu thích (Favorite)
*   **Toggle:** `POST /api/v1/resources/{id}/favorite`

---

## 📝 Ghi chú cho FE
1.  **Tab "Bài của tôi":** Trong trang quản lý tài liệu của Giáo viên, thêm tab "Của tôi" và gọi API với `mine=true`.
2.  **Thùng rác:** Thêm tab "Thùng rác" (cho Admin) gọi API với `status=DELETED`. Ở đây hiển thị nút "Khôi phục".
3.  **Rating:** Khi hiển thị chi tiết tài liệu, hiển thị số sao trung bình (`averageRating` trong Resource object).
4.  **Comment List:** Load danh sách comment ở dưới chi tiết tài liệu, có phân trang.
