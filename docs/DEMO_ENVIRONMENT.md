# Môi trường DEMO — tách khỏi Local

Tài liệu vận hành cho môi trường demo công khai. Demo chạy **song song** với
Local trên cùng một máy nhưng **không dùng chung** database, Redis, MinIO,
secret hay source đang phát triển.

---

## 1. Kiến trúc

Chỉ **hai** hostname được public. Backend và MinIO không có đường vào từ
Internet — nginx của mỗi frontend proxy chúng trong docker network, nên mọi
request đi cùng một origin.

```
demo.programmingwithtuyen.com ──┐
                                 ├─ Cloudflare Named Tunnel ─→ 127.0.0.1
admin-demo.programmingwithtuyen.com ┘

  127.0.0.1:4301 → warehouse_demo_portal (nginx)
  127.0.0.1:4300 → warehouse_demo_admin  (nginx)
        │
        ├── /                       → Angular static (build -c demo)
        ├── /api/                   → warehouse_demo_app   :8080  (docker network)
        └── /warehouse-demo-bucket/ → warehouse_demo_minio :9000  (docker network)

  warehouse_demo_app ──→ warehouse_demo_mysql / _redis / _minio
```

Same-origin nên: **không cần CORS**, **không cần `SameSite=None`**, và FE luôn
dùng `apiUrl: '/api/v1'`. Chuyển cả stack từ máy local sang VPS **không phải
build lại frontend**.

---

## 2. Local và Demo khác nhau ở đâu

| | Local | Demo |
|---|---|---|
| Compose project | `kindergarten-warehouse` | `warehouse_demo` |
| Env file | `.env` | `.env.demo` |
| Spring profile | `dev` | `prod,demo` |
| Container | `warehouse_*` | `warehouse_demo_*` |
| Database | `warehouse_db` | `warehouse_demo_db` |
| Bucket MinIO | `warehouse-bucket` | `warehouse-demo-bucket` |
| MySQL / Redis | 3306 / 6379 | 3307 / 6380 |
| MinIO API / Console | 9000 / 9001 | 9010 / 9011 |
| Backend | 8080 | 8081 |
| Frontend | dev-server 4200 / 4201 | nginx 4300 / 4301 |
| Schema | `ddl-auto: update` | Flyway V1→V22, `ddl-auto: validate` |
| Frontend | watch + hot reload | static build, **không** tự reload |
| Seed teacher | có (`teacher_hoa`/`teacher123`) | **không** |
| Secret | dùng chung của dev | sinh riêng, không trùng Local |

Volume tách nhờ project name: `warehouse_demo_mysql_data`,
`warehouse_demo_minio_data`, `warehouse_demo_redis_data`.

---

## 3. Chuẩn bị một lần

`.env.demo` đã được tạo sẵn với secret sinh ngẫu nhiên và **đã nằm trong
`.gitignore`**. Mở file để lấy `APP_ADMIN_PASSWORD` khi đăng nhập admin demo.

Cấu hình Named Tunnel: xem [docker/demo/cloudflared-config.example.yml](../docker/demo/cloudflared-config.example.yml).

---

## 4. Chạy demo

```bash
# 1) Build frontend dạng static (bắt buộc, nginx mount thư mục dist)
cd kindergarten-warehouse-angular
npx nx run-many -t build -p portal admin -c demo

# 2) Bật stack demo
cd ../kindergarten-warehouse
docker compose -p warehouse_demo --env-file .env.demo \
  -f docker-compose.yml -f docker-compose.demo.yml up -d --build

# 3) Bật tunnel
cloudflared tunnel run warehouse-demo
```

Kiểm tra tại chỗ trước khi mở tunnel:

```bash
curl -I http://127.0.0.1:4301/                       # Portal  → 200
curl -I http://127.0.0.1:4300/                       # Admin   → 200
curl -s http://127.0.0.1:8081/actuator/health        # Backend → {"status":"UP"}
```

---

## 5. Cập nhật demo sau khi sửa code

Demo là bản build tĩnh nên **không** tự cập nhật theo code đang sửa — đó chính
là mục đích tách môi trường.

```bash
# Đổi frontend
cd kindergarten-warehouse-angular
npx nx run-many -t build -p portal admin -c demo
# nginx mount thẳng thư mục dist ⇒ chỉ cần tải lại trang, không phải restart

# Đổi backend
cd ../kindergarten-warehouse
docker compose -p warehouse_demo --env-file .env.demo \
  -f docker-compose.yml -f docker-compose.demo.yml up -d --build app
```

---

## 6. Reset dữ liệu demo

Xoá sạch database, Redis và file demo rồi dựng lại từ đầu. Flyway chạy lại
V1→V22, `DataSeeder` tạo lại admin, danh mục và banner. **Không ảnh hưởng
Local.**

```bash
docker compose -p warehouse_demo --env-file .env.demo \
  -f docker-compose.yml -f docker-compose.demo.yml down -v

docker compose -p warehouse_demo --env-file .env.demo \
  -f docker-compose.yml -f docker-compose.demo.yml up -d
```

## 7. Tắt demo (giữ nguyên dữ liệu)

```bash
docker compose -p warehouse_demo --env-file .env.demo \
  -f docker-compose.yml -f docker-compose.demo.yml down
```

---

## 8. Giới hạn đã biết

Cần nói rõ với người xem demo:

| Vấn đề | Trạng thái |
|---|---|
| Dashboard admin | Dùng mock in-memory (`dashboard.service.ts`), **không** phải số liệu thật |
| Favorites | Backend **chưa có** endpoint nào; FE trả danh sách rỗng cố định |
| Gửi email | `MAIL_USERNAME` để trống ⇒ quên mật khẩu / xác thực email sẽ lỗi |
| Tài liệu mẫu | Seeder chỉ tạo admin + danh mục + banner; **chưa có** resource nào |

Đã sửa trong đợt này: FE gọi `/api/v1/me/resources` trong khi backend phục vụ
`/api/v1/resources/me` — trang "Tài liệu của tôi" trước đó luôn 404.

---

## 9. Checklist kiểm thử từ thiết bị khác

Phải test bằng máy/điện thoại **khác**, qua đúng hostname demo — không phải
`localhost` — mới phát hiện được lỗi cookie và đường dẫn tuyệt đối.

- [ ] Mở Portal, danh sách tài liệu và banner hiển thị
- [ ] Ảnh/thumbnail/avatar lên hình (đường dẫn phải là `/warehouse-demo-bucket/...`, **không** chứa `localhost`)
- [ ] Đăng nhập admin bằng `APP_ADMIN_PASSWORD` trong `.env.demo`
- [ ] Tải lại trang sau khi đăng nhập — phiên vẫn còn (cookie `Secure` + `SameSite=Lax`)
- [ ] Deep link trực tiếp (ví dụ `/resources/abc`) — nginx fallback về `index.html`, không 404
- [ ] Upload một file (kiểm tra giới hạn 50MB của nginx)
- [ ] Download tài liệu vừa upload — phải đi qua `/api`, không lộ link MinIO
- [ ] Đăng xuất
- [ ] Xác nhận sửa code Angular **không** làm đổi trang demo
- [ ] Xác nhận thao tác trên demo **không** đổi dữ liệu Local (`warehouse_db`)
