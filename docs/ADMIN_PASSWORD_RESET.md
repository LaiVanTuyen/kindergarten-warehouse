# Đổi mật khẩu admin đã seed

Sau khi `DataSeeder` đã tạo account admin (lần boot đầu tiên), việc thay đổi biến env `APP_ADMIN_PASSWORD` **sẽ không tự đồng bộ** — vì seed chỉ chạy nếu username chưa tồn tại.

Có 3 cách xử lý, **ưu tiên cách 1**.

---

## Cách 1 — User flow "Quên mật khẩu" (khuyến nghị)

Không cần thao tác DB, hoạt động trên mọi môi trường, có audit log.

```bash
# Bước 1: yêu cầu OTP
curl -X POST https://<domain>/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@your-domain.com"}'

# Bước 2: kiểm tra hộp thư của admin, lấy OTP 6 chữ số

# Bước 3: đặt password mới
curl -X POST https://<domain>/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@your-domain.com",
    "otp": "123456",
    "newPassword": "NewStrongPassword!234"
  }'
```

Yêu cầu: SMTP đã hoạt động ở env này. Nếu chưa cấu hình, OTP sẽ được log ở level `DEBUG` (chỉ ở profile dev/local).

---

## Cách 2 — Update trực tiếp DB (emergency)

Dùng khi SMTP chưa hoạt động ở lần deploy đầu tiên.

### Bước 1: sinh BCrypt hash của password mới

Dùng `jshell` (cần Java 17):

```bash
jshell
```

```java
jshell> /env --class-path ~/.m2/repository/org/springframework/security/spring-security-crypto/6.2.0/spring-security-crypto-6.2.0.jar
jshell> import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
jshell> new BCryptPasswordEncoder().encode("NewStrongPassword!234")
$X ==> "$2a$10$...."
```

Hoặc dùng Python:

```bash
pip install bcrypt
python -c "import bcrypt; print(bcrypt.hashpw(b'NewStrongPassword!234', bcrypt.gensalt()).decode())"
```

### Bước 2: update DB

```sql
UPDATE users
SET password = '$2a$10$...<hash từ bước 1>',
    token_version = token_version + 1
WHERE username = 'admin';
```

> **Lưu ý**: `token_version + 1` để vô hiệu hóa toàn bộ JWT đã phát hành cho admin (nếu đang đăng nhập ở đâu đó sẽ bị kick ra).

### Bước 3: xóa cache user

```bash
# Kết nối Redis rồi xóa key cache
redis-cli -h <redis-host>
> DEL kindergarten:tv:<admin-user-id>
> DEL "users::admin"
> DEL "users::admin@your-domain.com"
```

Hoặc đơn giản restart app — cache Redis sẽ được load lại.

---

## Cách 3 — Reset hoàn toàn account admin (nuclear)

Chỉ dùng khi account admin bị hỏng dữ liệu không khôi phục được.

```sql
-- Backup trước khi chạy
SELECT * FROM users WHERE username = 'admin';

-- Xóa cứng account admin
DELETE FROM user_roles WHERE user_id = (SELECT id FROM users WHERE username = 'admin');
DELETE FROM users WHERE username = 'admin';
```

Sau đó:
1. Cập nhật `APP_ADMIN_PASSWORD` trong `.env.prod` với password mới.
2. Restart app — `DataSeeder` sẽ tạo lại admin với password mới.

> **Cảnh báo**: cách này sẽ **mất dữ liệu liên kết** (audit log tham chiếu, resource tạo bởi admin, v.v.) nếu có FK `ON DELETE SET NULL`. Kiểm tra kỹ trước khi chạy.
