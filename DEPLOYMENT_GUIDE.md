# 🚀 Hướng dẫn Triển khai Production (Deployment Guide)

Tài liệu này hướng dẫn cách triển khai dự án **Kindergarten Warehouse** lên môi trường Production (VPS/Cloud) sử dụng Docker Compose.

---

## 1. Yêu cầu Hệ thống (Prerequisites)

*   **Server (VPS):**
    *   OS: Ubuntu 20.04/22.04 LTS.
    *   RAM: Tối thiểu 2GB (Khuyên dùng 4GB).
    *   CPU: 2 Core.
    *   Disk: 20GB SSD.
*   **Phần mềm:**
    *   Docker & Docker Compose.
    *   Git.
*   **Domain:** Đã trỏ về IP của VPS.

---

## 2. Chuẩn bị Mã nguồn & Cấu hình

### Bước 1: Clone Repository
SSH vào VPS và clone code về:
```bash
git clone https://github.com/your-username/kindergarten-warehouse.git
cd kindergarten-warehouse
```

### Bước 2: Tạo file biến môi trường (.env)
Tạo file `.env` tại thư mục gốc. **KHÔNG** commit file này lên Git.

```bash
nano .env
```

**Nội dung mẫu `.env`:**
```env
# Domain (Quan trọng cho MinIO Redirect)
DOMAIN_NAME=your-domain.com

# Database
MYSQL_ROOT_PASSWORD=**************
MYSQL_DATABASE=warehouse_db
MYSQL_USER=warehouse_user
MYSQL_PASSWORD=**************

# Redis
REDIS_PASSWORD=**************

# MinIO (S3 Storage)
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=**************
MINIO_BUCKET_NAME=resources

# App Config
APP_PORT=8080
JWT_SECRET=**************
JWT_EXPIRATION=86400000
CORS_ALLOWED_ORIGINS=https://your-domain.com,https://admin.your-domain.com
```

---

## 3. Triển khai bằng Docker Compose

**Lưu ý quan trọng:**
1.  **Multi-stage Build:** Dockerfile đã được cấu hình Multi-stage, nên bạn **KHÔNG** cần cài Java hay Maven trên VPS. Docker sẽ tự build file .jar.
2.  **Data Persistence:** File `docker-compose.yml` đã cấu hình `volumes` để lưu dữ liệu MySQL, Redis, MinIO ra ổ cứng máy chủ. Dữ liệu sẽ không bị mất khi restart container.

### Bước 1: Build & Run
Tại thư mục gốc dự án, chạy lệnh:

```bash
# Build ứng dụng và khởi động các container
docker-compose up -d --build
```

### Bước 2: Kiểm tra trạng thái
```bash
docker-compose ps
```
Đảm bảo tất cả container đều ở trạng thái `Up` (hoặc `healthy`).

### Bước 3: Xem log (Nếu có lỗi)
```bash
docker-compose logs -f app
```

---

## 4. Cấu hình Nginx & SSL (HTTPS)

Chúng ta sử dụng Nginx làm Reverse Proxy để bảo mật (ẩn port 8080) và cấu hình SSL.

### Bước 1: Cài đặt Nginx & Certbot
```bash
sudo apt update
sudo apt install nginx certbot python3-certbot-nginx
```

### Bước 2: Cấu hình Nginx
Tạo file config:
```bash
sudo nano /etc/nginx/sites-available/kindergarten
```

**Nội dung:**
```nginx
server {
    server_name your-domain.com;

    # Backend API
    location / {
        proxy_pass http://127.0.0.1:8080; # Chỉ gọi vào localhost
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Support upload file lớn (Video)
        client_max_body_size 500M;
    }

    # MinIO Console (Quản lý file)
    location /minio/ {
        proxy_pass http://127.0.0.1:9001/;
        proxy_set_header Host $host;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        chunked_transfer_encoding off;
    }
}
```

Kích hoạt site:
```bash
sudo ln -s /etc/nginx/sites-available/kindergarten /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### Bước 3: Cài SSL (HTTPS)
```bash
sudo certbot --nginx -d your-domain.com
```

---

## 5. Vận hành & Bảo trì

### Backup Database (Full)
```bash
# Backup toàn bộ cấu trúc, dữ liệu, hàm và trigger
docker exec warehouse_mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} --routines --triggers --databases warehouse_db > backup_$(date +%F).sql
```

### Cập nhật Code mới
```bash
git pull
docker-compose up -d --build app
```
(Lệnh này chỉ build lại container `app`, Database và MinIO vẫn chạy bình thường).

---

## 6. Lưu ý Bảo mật
1.  **Firewall:** Chỉ mở port 80, 443 (Nginx) và 22 (SSH). **TUYỆT ĐỐI KHÔNG** mở port 8080, 3306, 6379, 9000, 9001 ra internet.
2.  **MinIO Public Bucket:**
    *   Truy cập MinIO Console tại `https://your-domain.com/minio/`.
    *   Tạo bucket `resources`.
    *   Set Access Policy là **Public** (Read Only) hoặc **Custom** để người dùng có thể xem ảnh/video.
