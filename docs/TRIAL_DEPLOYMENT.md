# Bản dùng thử cho giáo viên — triển khai miễn phí trên Oracle Cloud

Mục tiêu: một bản chạy online để 5–20 giáo viên dùng thử vài tuần, chi phí ≈ 0, dữ liệu sao lưu được.

## 1. Kiến trúc đã chọn và vì sao

```
Giáo viên ──HTTPS──► Cloudflare (tên miền programmingwithtuyen.com, miễn phí)
                         │  Cloudflare Tunnel (đi từ máy chủ ra, không mở cổng)
                         ▼
             Oracle Cloud Always Free — 1 máy Ampere A1 (ARM)
   ┌───────────────────────────────────────────────────────────┐
   │ cloudflared → nginx Portal ─┬─ /            Angular Portal  │
   │             → nginx Admin  ─┼─ /api/        Spring Boot app │
   │                             └─ /<bucket>/   MinIO (ảnh)     │
   │ MySQL 8 · Redis 7 · MinIO · LibreOffice (preview Office)    │
   └───────────────────────────────────────────────────────────┘
```

Hai điểm **khác** với gợi ý "Cloudflare Pages + R2", đã kiểm tra trong code:

| Gợi ý | Vấn đề | Thay bằng |
|---|---|---|
| Portal/Admin trên Cloudflare Pages, API trên máy khác | Backend chống CSRF bằng cookie `XSRF-TOKEN` mà giao diện phải **đọc bằng JavaScript**. Giao diện ở `*.pages.dev` không đọc được cookie của tên miền API → mọi thao tác ghi (đăng nhập, lưu, tải lên) bị 403. | Giao diện và API **cùng origin**: nginx của mỗi app proxy `/api` (đã có sẵn ở `docker-compose.demo.yml`). |
| Kho tệp trên Cloudflare R2 | Hệ thống dùng **bucket policy** để chỉ công khai ảnh (avatar, thumbnail, banner) còn tệp học liệu riêng tư. R2 không hỗ trợ bucket policy; public của R2 là cả bucket → hoặc mất thumbnail, hoặc **lộ toàn bộ tệp học liệu**. | MinIO ngay trên máy Oracle (200 GB đĩa là đủ cho bản thử). |

Cloudflare Tunnel thay cho mở cổng 80/443: không cần cấu hình firewall Oracle, có HTTPS miễn phí, tên miền cố định. Bạn đã dùng cách này cho bản demo.

## 2. Mức độ sẵn sàng (11/10/2026)

**Đã có và có test tự động** (backend: unit + tích hợp MySQL thật; frontend: Jest + build):
kho học liệu (tìm kiếm không dấu, tag, sắp xếp, xem trước PDF/Office, tải về, yêu thích, đánh giá), báo cáo vi phạm + gỡ tài liệu, thông báo, trang tác giả, lịch sử tải, bộ học liệu, kế hoạch hoạt động (mẫu, sao chép, in/PDF), chế độ trình chiếu, trợ lý AI (khi có key), thư viện trường + duyệt chuyên môn + góp ý + "đã sử dụng".

**Chưa kiểm chứng — làm trước khi mời giáo viên:**

| Việc | Vì sao |
|---|---|
| Thử trên trình duyệt: tải lên MP3, phát MP3/MP4 và tua trong màn trình chiếu (endpoint `/stream`) | Mới có test tự động với kho tệp giả lập. |
| Thử trên trình duyệt: duyệt chuyên môn, góp ý, "đã dùng", thư viện trường | Giao diện giai đoạn 3 mới có test Jest. |
| Gọi trợ lý AI với key thật (nếu bật) | Chưa có lần gọi Claude thật nào. |
| Build image backend trên máy ARM | LibreOffice/ảnh nền đều có bản arm64 nhưng chưa build thử trên Ampere. |
| Xoá mật khẩu tài khoản demo khỏi repo **trước khi push** | Đang có trong `scripts/demo-acceptance.cjs`, `scripts/prepare-demo-accounts.ps1` và 3 báo cáo trong `docs/`; repo được ghi là public. |
| Nội dung ban đầu | Danh mục, chủ đề, nhóm tuổi và vài chục học liệu mẫu có bản quyền rõ ràng, để giáo viên có cái để dùng ngay. |

**Giới hạn đã biết của bản thử:** không gửi email (giáo viên không tự đăng ký — quản trị viên tạo tài khoản), một máy chủ duy nhất (không dự phòng), Oracle có thể thu hồi máy Always Free ít hoạt động, sao lưu phải chép ra ngoài thủ công.

## 3. Chuẩn bị (một lần)

1. **Oracle Cloud**: tạo tài khoản → Compute → Create instance → *Ampere A1*, Ubuntu 22.04, 2 OCPU / 12 GB RAM, boot volume 100–150 GB. Nếu báo hết tài nguyên, thử Availability Domain khác hoặc thử lại sau.
2. **Cloudflare**: Zero Trust → Networks → Tunnels → *Create a tunnel* (Cloudflared) → đặt tên `warehouse-trial` → chọn *Docker*, **chép token**. Thêm 2 Public Hostname:
   - `thu-nghiem.programmingwithtuyen.com` → `http://warehouse_trial_portal:80`
   - `admin-thu-nghiem.programmingwithtuyen.com` → `http://warehouse_trial_admin:80`
3. **Trên máy Oracle**:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2 git openssl
   sudo usermod -aG docker $USER && newgrp docker
   sudo mkdir -p /srv/kw /srv/kw-backups && sudo chown $USER /srv/kw /srv/kw-backups
   cd /srv/kw
   git clone <repo-backend> kindergarten-warehouse
   git clone <repo-angular> kindergarten-warehouse-angular
   ```

## 4. Cấu hình và chạy

```bash
cd /srv/kw/kindergarten-warehouse
cp .env.trial.example .env.trial && chmod 600 .env.trial
./scripts/generate-secrets.sh          # chép các giá trị sinh ra vào .env.trial
nano .env.trial                        # điền tên miền, email admin, CLOUDFLARE_TUNNEL_TOKEN

./scripts/build-frontends.sh           # build Portal + Admin trong container Node (ARM được)

docker compose -p warehouse_trial --env-file .env.trial \
  -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.trial.yml up -d --build
docker compose -p warehouse_trial logs -f app     # chờ "Started WarehouseApplication"
```

Mở `https://admin-thu-nghiem.…`, đăng nhập bằng `APP_ADMIN_*`, rồi:
- tạo danh mục, chủ đề, nhóm tuổi; tải lên học liệu mẫu;
- **Người dùng → Tạo** tài khoản vai trò TEACHER cho từng giáo viên (không cần email OTP);
- (tuỳ chọn) soạn 2–3 kế hoạch, tick *Dùng làm mẫu*.

Cập nhật phiên bản mới:
```bash
git -C ../kindergarten-warehouse-angular pull && ./scripts/build-frontends.sh
git pull && docker compose -p warehouse_trial --env-file .env.trial \
  -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.trial.yml up -d --build
```
Flyway tự chạy migration mới khi app khởi động. **Sao lưu trước** mỗi lần cập nhật.

## 5. Sao lưu và khôi phục

```bash
crontab -e
15 2 * * *  cd /srv/kw/kindergarten-warehouse && ./scripts/backup-trial.sh >> /srv/kw-backups/backup.log 2>&1
```
Mỗi bản gồm `mysql.sql.gz` + `minio.tar.gz`, giữ 14 ngày. Script dừng (không xoá bản cũ) nếu bản dump rỗng.
**Hằng tuần chép ra ngoài máy chủ**, ví dụ từ máy của bạn: `scp -r ubuntu@<ip>:/srv/kw-backups/<ngày> .`

Khôi phục vào stack đang chạy:
```bash
set -a; . ./.env.trial; set +a
gunzip -c <bản>/mysql.sql.gz | docker exec -i warehouse_trial_mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
docker compose -p warehouse_trial stop minio
docker run --rm -v warehouse_trial_minio_data:/data -v "$PWD/<bản>":/b alpine sh -c 'rm -rf /data/* && tar xzf /b/minio.tar.gz -C /data'
docker compose -p warehouse_trial start minio && docker compose -p warehouse_trial restart app
```
Nên thử khôi phục một lần trên máy khác trước khi giáo viên nhập dữ liệu thật.

## 6. Trợ lý AI (tuỳ chọn)

Đặt `ANTHROPIC_API_KEY` trong `.env.trial` rồi `up -d` lại. Không có key thì trợ lý tự ẩn. Dữ liệu gửi đi: nội dung kế hoạch và thông tin tài liệu (tên, mô tả, tag, độ tuổi) — không có tên/email giáo viên, ghi nhận sau buổi học hay thông tin trẻ. Nên có sự đồng ý của nhà trường trước khi bật. Hạn mức mặc định 30 yêu cầu/giáo viên/ngày (`AI_DAILY_LIMIT`).

## 7. Khi nào cần chuyển sang dịch vụ trả phí

Khi trường dùng hằng ngày: máy chủ có cam kết (không bị thu hồi), sao lưu tự động ra nơi khác, email gửi OTP/thông báo, và theo dõi lỗi (Rollbar). Kiến trúc trên giữ nguyên, chỉ đổi nơi chạy.
