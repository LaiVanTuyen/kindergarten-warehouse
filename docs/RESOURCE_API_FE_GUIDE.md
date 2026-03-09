# Hướng dẫn API Resource cho FE

## Phạm vi
Tài liệu này mô tả hành vi theo định hướng production cho các API liên quan tới Resource để FE đồng bộ luồng UI và xử lý các case biên.

## Xác thực
- Các endpoint public: list, detail, download file (nếu resource public), view.
- Các endpoint yêu cầu đăng nhập: upload, update, delete/restore, favorite.
- Header chuẩn:
```
Authorization: Bearer <access_token>
```

## Chuẩn dữ liệu chung
- Thời gian trả về theo ISO-8601, ví dụ `2026-02-24T09:00:00`.
- `resourceType`: `FILE` | `YOUTUBE`.
- `status`: `PENDING` | `APPROVED` | `REJECTED`.
- `fileType`: `VIDEO` | `PDF` | `DOCUMENT` | `EXCEL` (backend tự suy ra).
- `duration`: chuỗi dạng `mm:ss` hoặc `hh:mm:ss` (YouTube).

## Giới hạn upload
- Kích thước file upload tối đa theo cấu hình backend: `50MB`.
- Thumbnail: tối đa theo cấu hình `app.resource.thumbnail-max-bytes` (mặc định 5MB), định dạng `JPG`, `PNG`, `WebP`.

## Cấu trúc response (phổ biến)
Hầu hết endpoint trả về dạng:
```
{
  "code": 0,
  "message": "...",
  "data": { ... }
}
```
Lỗi trả về cấu trúc tương tự với `code` và `message`.

Gợi ý schema `ResourceResponse`:
```json
{
  "id": "uuid",
  "title": "string",
  "slug": "string",
  "description": "string",
  "viewsCount": 0,
  "fileUrl": "string",
  "thumbnailUrl": "string",
  "resourceType": "FILE",
  "fileType": "PDF",
  "fileExtension": "pdf",
  "fileSize": 1024,
  "duration": "05:30",
  "status": "APPROVED",
  "downloadCount": 0,
  "averageRating": 0,
  "topic": { "id": 1, "name": "...", "slug": "..." },
  "ageGroups": [ { "id": 1, "name": "..." } ],
  "visibility": "PUBLIC",
  "isFavorited": false,
  "rejectionReason": "Lỗi bản quyền hình ảnh, vui lòng thay ảnh thumbnail hoặc nội dung PDF",
  "createdAt": "2026-02-24T09:00:00",
  "updatedAt": "2026-02-24T09:10:00",
  "createdBy": "...",
  "updatedBy": "..."
}
```

> [!IMPORTANT]
> **YÊU CẦU XÓA RÁC CODE:** FE hãy xóa hoặc loại bỏ hoàn toàn trường `isActive` (boolean) khỏi tất cả các interface/model cũ. Hệ thống giờ đây sử dụng luồng `status` và `visibility` độc lập.

## Gợi ý hiển thị UI
- `resourceType=FILE`: hiển thị nút tải (`GET /{id}/file`), có thể hiển thị dung lượng (`fileSize`) và loại file (`fileType`).
- `resourceType=YOUTUBE`: không có download, hiển thị nút mở YouTube (`fileUrl`), dùng `thumbnailUrl` (YouTube auto) và `duration`.
- Nếu `isFavorited=true`: hiển thị trạng thái đã yêu thích.
- Nếu `status!=APPROVED`: FE chỉ nên hiển thị ở khu vực quản trị.

## Endpoints

### 1) Danh sách resource (Dành cho Khách/Học sinh)
`GET /api/v1/resources`

Query params:
- `page` (mặc định 0)
- `size` (mặc định 10)
- `keyword`
- `topicSlugs` (hỗ trợ phân tách bằng dấu phẩy, ví dụ `?topicSlugs=stem,art`)
- `categorySlugs`
- `ageSlugs`
- `types` (loại file như `VIDEO`, `PDF`, `DOCUMENT`)

Ghi chú hành vi:
- API này dành cho Portal công khai. Nó CHỈ trả về resource thỏa mãn điều kiện: đã duyệt (`status=APPROVED`), đang công khai (`visibility=PUBLIC`), chưa bị xóa, và Category/Topic đang hoạt động.
- Không cần gửi Auth token cho API này. Các tham số về status sẽ bị bỏ qua.

Ví dụ request:
```
GET /api/v1/resources?page=0&size=10&keyword=toan&topicSlugs=stem,art&types=PDF,VIDEO
```

### 1.1) Danh sách "Tài liệu của tôi" (Dành cho Giáo viên/User tải lên)
`GET /api/v1/resources/me`

Query params:
- (Giống hệ thống tham số của bản Public ở trên)
- `status` (Có thể lọc theo `PENDING`, `APPROVED`, `REJECTED`)

Ghi chú hành vi:
- **YÊU CẦU ĐĂNG NHẬP**. Dùng để user quản trị tài liệu do chính mình up. 
- API này trả về mọi tài liệu do user này tạo, bất chấp là PRIVATE hay PENDING.

Ví dụ request:
```
GET /api/v1/resources/me?page=0&size=10&status=PENDING
```

### 1.2) Danh sách toàn hệ thống (Dành cho CMS Admin)
`GET /api/v1/admin/resources`

Query params:
- (Giống hệ thống tham số của bản Public ở trên)
- `status` (Có thể lọc theo `DELETED`, `PENDING`, `APPROVED`, `REJECTED`)

Ghi chú hành vi:
- **YÊU CẦU QUYỀN ADMIN**. Trả về Error 403 nếu user thường cố gắng chui vào.
- Trả về TOÀN BỘ dữ liệu trên DB, cho phép Admin truy tìm cả bài bị xóa (status=DELETED) hoặc bị thiết lập PRIVATE.

Ví dụ request:
```
GET /api/v1/admin/resources?page=0&size=10&status=DELETED
```

Ví dụ response:
```json
{
  "code": 0,
  "message": "resource.list.success",
  "data": {
    "content": [
      {
        "id": "2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111",
        "title": "Bai tap toan lop mam",
        "slug": "bai-tap-toan-lop-mam-1700000000000",
        "description": "...",
        "viewsCount": 120,
        "fileUrl": "https://minio.example.com/warehouse-bucket/resources/files/uuid.pdf",
        "thumbnailUrl": "https://minio.example.com/warehouse-bucket/resources/thumbnails/uuid.png",
        "resourceType": "FILE",
        "fileType": "PDF",
        "fileExtension": "pdf",
        "fileSize": 102400,
        "duration": null,
        "status": "APPROVED",
        "downloadCount": 5,
        "averageRating": 4.8,
        "topic": {
          "id": 10,
          "name": "Stem co ban",
          "slug": "stem-co-ban"
        },
        "ageGroups": [
          { "id": 1, "name": "3-4" }
        ],
        "visibility": "PUBLIC",
        "isFavorited": false,
        "createdAt": "2026-02-24T09:00:00",
        "updatedAt": "2026-02-24T09:10:00",
        "createdBy": "Nguyen Van A",
        "updatedBy": "Nguyen Van A"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 10
    },
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 2) Chi tiết resource theo slug
`GET /api/v1/resources/{slug}`

Ghi chú hành vi:
- Public/non-privileged chỉ thấy resource đã duyệt + active + chưa xóa + topic/category chưa xóa và đang active.
- Ngược lại trả `RESOURCE_NOT_FOUND` (không trả 403) để tránh lộ sự tồn tại.

Ví dụ request:
```
GET /api/v1/resources/bai-tap-toan-lop-mam-1700000000000
```

Ví dụ response:
```json
{
  "code": 0,
  "message": "resource.detail.success",
  "data": {
    "id": "2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111",
    "title": "Bai tap toan lop mam",
    "slug": "bai-tap-toan-lop-mam-1700000000000",
    "description": "...",
    "viewsCount": 120,
    "fileUrl": "https://minio.example.com/warehouse-bucket/resources/files/uuid.pdf",
    "thumbnailUrl": "https://minio.example.com/warehouse-bucket/resources/thumbnails/uuid.png",
    "resourceType": "FILE",
    "fileType": "PDF",
    "fileExtension": "pdf",
    "fileSize": 102400,
    "duration": null,
    "status": "APPROVED",
    "downloadCount": 5,
    "averageRating": 4.8,
    "topic": {
      "id": 10,
      "name": "Stem co ban",
      "slug": "stem-co-ban"
    },
    "ageGroups": [
      { "id": 1, "name": "3-4" }
    ],
    "visibility": "PUBLIC",
    "isFavorited": false,
    "createdAt": "2026-02-24T09:00:00",
    "updatedAt": "2026-02-24T09:10:00",
    "createdBy": "Nguyen Van A",
    "updatedBy": "Nguyen Van A"
  }
}
```

Ví dụ response (YouTube):
```json
{
  "code": 0,
  "message": "resource.detail.success",
  "data": {
    "id": "b1f8c0b1-2d8f-4f8f-9f9a-5d6a0c6e0001",
    "title": "Video hoc hat",
    "slug": "video-hoc-hat-1700000000000",
    "description": "...",
    "viewsCount": 320,
    "fileUrl": "https://www.youtube.com/watch?v=abc123",
    "thumbnailUrl": "https://img.youtube.com/vi/abc123/hqdefault.jpg",
    "resourceType": "YOUTUBE",
    "fileType": "VIDEO",
    "fileExtension": "youtube",
    "fileSize": 0,
    "duration": "05:30",
    "status": "APPROVED",
    "downloadCount": 0,
    "averageRating": 4.9,
    "topic": {
      "id": 10,
      "name": "Am nhac",
      "slug": "am-nhac"
    },
    "ageGroups": [
      { "id": 2, "name": "4-5" }
    ],
    "visibility": "PUBLIC",
    "isFavorited": true,
    "createdAt": "2026-02-24T09:00:00",
    "updatedAt": "2026-02-24T09:10:00",
    "createdBy": "Nguyen Van B",
    "updatedBy": "Nguyen Van B"
  }
}
```

### 3) Upload resource
`POST /api/v1/resources` (multipart/form-data)

Fields:
- `title` (bắt buộc)
- `description` (tùy chọn)
- `topicId` (bắt buộc)
- `ageGroupIds` (tùy chọn, array)
- `file` (tùy chọn)
- `youtubeLink` (tùy chọn)
- `thumbnail` (tùy chọn)
- `duration` (tùy chọn, cho YouTube; backend có thể tự lấy)

Ghi chú thêm:
- Với `ageGroupIds` dạng array, FE có thể gửi nhiều field `ageGroupIds=1&ageGroupIds=2`.
- `fileType` không gửi từ FE; backend tự suy ra.

Quy tắc:
- Chỉ được gửi đúng một trong hai: `file` hoặc `youtubeLink`.
- Nếu gửi cả hai hoặc thiếu cả hai -> `INVALID_REQUEST`.
- `status` do backend gán: ADMIN -> APPROVED, các role khác -> PENDING.

Ví dụ request (file):
```
POST /api/v1/resources
Content-Type: multipart/form-data

file=@/path/to/file.pdf
thumbnail=@/path/to/thumb.png
title="Bai tap toan"
description="..."
topicId=10
ageGroupIds=1
```

Ví dụ request (YouTube):
```
POST /api/v1/resources
Content-Type: multipart/form-data

youtubeLink="https://www.youtube.com/watch?v=abc123"
thumbnail=@/path/to/thumb.png
title="Video hoc hat"
description="..."
topicId=10
ageGroupIds=1
```

### 4) Cập nhật resource
`PUT /api/v1/resources/{id}` (JSON hoặc multipart/form-data)

Fields:
- `title`, `description`, `topicId`, `ageGroupIds`
- `file` HOẶC `youtubeLink` (loại trừ lẫn nhau)
- `thumbnail`
- `status` (chỉ ADMIN)
- `fileType` (bị bỏ qua; backend tự suy ra từ file/link)

Quy tắc:
- Chỉ owner hoặc ADMIN được cập nhật.
- Không được gửi đồng thời file và YouTube link.
- **Workflow Bị Từ Chối (REJECTED):** Khi Owner (Người đăng tải) gọi API cập nhật nội dung cho một tài liệu đang bị từ chối, hệ thống sẽ **Tự động chuyển status về PENDING** và **Xóa rỗng rejectionReason** để chờ duyệt lại. Frontend chỉ việc gọi PUT nội dung bình thường, không cần truyền status.
- Admin cập nhật nội dung thì hệ thống giữ nguyên status hiện tại (không bị auto-pending). Muốn Admin ép duyệt thì gọi PATCH `/approve` riêng. Trang này admin mới được chủ động sửa trường `status` bằng tay qua PUT.

Ví dụ request (JSON):
```json
PUT /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111
{
  "title": "Bai tap toan cap nhat",
  "description": "Mo ta moi",
  "topicId": 11,
  "ageGroupIds": [1, 2]
}
```

Ví dụ request (multipart):
```
PUT /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111
Content-Type: multipart/form-data

file=@/path/to/file.pdf
thumbnail=@/path/to/thumb.png
```

### 5) Download file resource
`GET /api/v1/resources/{id}/file`

Hành vi:
- Chỉ resource dạng FILE mới được download. Nếu là YouTube -> `INVALID_REQUEST`.
- Endpoint này tự tăng download count. FE nên dùng endpoint này khi tải.

Ghi chú header:
- Response có `Content-Disposition` để gợi ý tên file.
- Response có `Content-Type` khớp với loại file.

Ví dụ request:
```
GET /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111/file
```

### 6) View count
`PUT /api/v1/resources/{id}/view`

Hành vi:
- View được de-dup theo IP trong 1 giờ.

Ví dụ request:
```
PUT /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111/view
```

### 7) Favorite toggle
`POST /api/v1/resources/{id}/favorite`

Hành vi:
- Toggle và trả `{ "isFavorited": true|false }`.

Ví dụ request:
```
POST /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111/favorite
```

Ví dụ response:
```json
{
  "code": 0,
  "message": "resource.favorite.success",
  "data": {
    "isFavorited": true
  }
}
```

### 8) Xóa và khôi phục
- `DELETE /api/v1/resources/{id}`
- `DELETE /api/v1/resources/bulk`
- `PUT /api/v1/resources/{id}/restore`
- `PATCH /api/v1/resources/bulk-restore`

Quy tắc:
- Chỉ owner hoặc ADMIN được xóa/khôi phục.
- `hard=true` là xóa vĩnh viễn và xóa file.

Ví dụ request (soft delete):
```
DELETE /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111
```

Ví dụ request (hard delete):
```
DELETE /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111?hard=true
```

Ví dụ request (bulk delete):
```json
DELETE /api/v1/resources/bulk?hard=false
[
  "2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111",
  "4a6c0f5e-8e4d-4d2b-9a9b-2c99b6ef8f22"
]
```

Ví dụ request (restore):
```
PUT /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111/restore
```

### 9) Cập nhật trạng thái hiển thị (Visibility)
`PATCH /api/v1/resources/{id}/visibility`

Hành vi:
- Giúp người đăng (Owner) tạm ẩn bài viết (từ `PUBLIC` -> `PRIVATE`) hoặc ngược lại.
- Giúp Admin ép hạ bài (Takedown) thành `PRIVATE` phục vụ điều tra mà không phải đánh dấu bài là REJECTED.

Ví dụ request:
```json
PATCH /api/v1/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111/visibility
Content-Type: application/json

{
  "visibility": "PRIVATE"
}
```

### 10) Duyệt Bài (Admin Tách Biệt)
`PATCH /api/v1/admin/resources/{id}/approve`

Hành vi:
- Dành riêng cho **Admin CMS**. Chuyển `status` thành `APPROVED`.
- Nếu bài đang có `rejectionReason` lưu từ trước, gọi API này sẽ tự động xóa sạch lý do lỗi đó.
- Không cần body.

### 11) Từ chối Bài (Admin Tách Biệt)
`PATCH /api/v1/admin/resources/{id}/reject`

Hành vi:
- Dành cho **Admin CMS** từ chối bài không đạt chuẩn. Chuyển trạng thái sang `REJECTED`.
- Bắt buộc phải truyền Body chứa trường `reason` (Lý do từ chối, tối đa 1000 ký tự). Lý do này sẽ được trả về trong cục Resource Detail cho Uploader thấy.

Ví dụ request:
```json
PATCH /api/v1/admin/resources/2d6f9c1e-5f2f-4e78-9e1f-3c92a7e7a111/reject
Content-Type: application/json

{
  "reason": "File PDF của bạn chứa những từ khóa không phù hợp với lứa tuổi mầm non."
}
```

## Mã lỗi FE cần xử lý
- `6001 RESOURCE_NOT_FOUND` -> hiển thị không tìm thấy
- `6004 RESOURCE_FORBIDDEN` -> hiển thị không đủ quyền
- `6005 INVALID_YOUTUBE_LINK` -> link YouTube không hợp lệ/không truy cập được
- `6006 INVALID_IMAGE_FORMAT` -> ảnh thu nhỏ sai định dạng
- `6007 THUMBNAIL_TOO_LARGE` -> ảnh thu nhỏ quá lớn
- `9001 INVALID_REQUEST` -> hiển thị lỗi validate

Gợi ý mapping HTTP:
- `400` -> validate/request không hợp lệ
- `403` -> không có quyền
- `404` -> không tồn tại hoặc không được phép xem

## Ví dụ lỗi
Ví dụ invalid YouTube link:
```json
{
  "code": 6005,
  "message": "error.resource.youtube.invalid",
  "data": null
}
```

Ví dụ thumbnail sai định dạng:
```json
{
  "code": 6006,
  "message": "error.resource.thumbnail.format.invalid",
  "data": null
}
```

Ví dụ thumbnail quá lớn:
```json
{
  "code": 6007,
  "message": "error.resource.thumbnail.too.large",
  "data": null
}
```
