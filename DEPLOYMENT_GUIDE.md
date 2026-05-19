# Deployment Guide — Kindergarten Warehouse

Tài liệu này mô tả flow deploy chuẩn **prod** với Docker Compose + VPS, đảm bảo **secrets không leak** vào git.

---

## Nguyên tắc quản lý secrets

```
┌────────────────────────────────────────────────────────────────┐
│ Repo GitHub (public)                                           │
│   ✅ .env.example         — template, không có secret thật     │
│   ✅ docker-compose.yml   — chỉ reference biến qua ${VAR}      │
│   ✅ scripts/*.sh         — deploy helper                      │
│   ❌ .env, .env.prod      — bị .gitignore, KHÔNG BAO GIỜ commit│
├────────────────────────────────────────────────────────────────┤
│ VPS Production                                                 │
│   /srv/kindergarten-warehouse/                                 │
│     ├── .env.prod         — chmod 600, owner `deploy`          │
│     └── … (source code từ git pull)                            │
├────────────────────────────────────────────────────────────────┤
│ GitHub Actions Secrets (optional, cho auto-deploy)             │
│   SSH_HOST, SSH_USER, SSH_PRIVATE_KEY, DEPLOY_PATH             │
│   → KHÔNG chứa secret của app, chỉ chứa credential để SSH      │
└────────────────────────────────────────────────────────────────┘
```

**Nguyên tắc vàng**:
- Secrets của **app** (JWT, DB pass, mail pass, …) sống duy nhất trong `.env.prod` trên **VPS**.
- GitHub Actions chỉ cần credential để **SSH vào VPS**, rồi gọi script deploy.
- Khi rotate secret → chỉnh `.env.prod` trên server → `./scripts/deploy.sh`. KHÔNG chạm git.

---

## 1. Yêu cầu

| Thành phần | Phiên bản |
|-----------|-----------|
| Ubuntu | 20.04 / 22.04 LTS |
| RAM | ≥ 2 GB (khuyến nghị 4 GB) |
| Disk | ≥ 20 GB SSD |
| Docker Engine | ≥ 24 (có Docker Compose V2 built-in) |
| Git | ≥ 2.30 |
| Domain | Đã trỏ A record về IP VPS |

---

## 2. Chuẩn bị VPS lần đầu

### 2.1. Tạo user không phải root

```bash
# Trên VPS, login root
adduser deploy
usermod -aG docker deploy
usermod -aG sudo deploy   # optional — chỉ nếu deploy cần sudo
su - deploy
```

### 2.2. Clone repo

```bash
sudo mkdir -p /srv && sudo chown deploy:deploy /srv
cd /srv
git clone https://github.com/LaiVanTuyen/kindergarten-warehouse.git
cd kindergarten-warehouse
```

### 2.3. Sinh secrets

```bash
chmod +x scripts/*.sh
./scripts/generate-secrets.sh > /tmp/generated-secrets.txt
cat /tmp/generated-secrets.txt
```

Output mẫu:
```
JWT_SECRET=eK8wN...48 ký tự base64
APP_ADMIN_PASSWORD=Kj2mQ...24 ký tự
MYSQL_ROOT_PASSWORD=...
MYSQL_PASSWORD=...
REDIS_PASSWORD=...
MINIO_ROOT_PASSWORD=...
```

### 2.4. Tạo `.env.prod`

```bash
cp .env.example .env.prod
chmod 600 .env.prod
nano .env.prod
```

Điền:
- Các secret vừa sinh ở bước 2.3.
- `DOMAIN_NAME=your-domain.com`
- `CORS_ALLOWED_ORIGINS=https://your-domain.com`
- `APP_ADMIN_EMAIL`, `MAIL_USERNAME`, `MAIL_PASSWORD` (Gmail App Password — **không** dùng pass Gmail thật).
- `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=None` (nếu FE khác domain) hoặc `Strict` (cùng domain).

Xóa file tạm:
```bash
shred -u /tmp/generated-secrets.txt
```

### 2.5. Validate

```bash
./scripts/check-env.sh .env.prod
```

Phải thấy dòng cuối: `Passed: N | Failed: 0`.

---

## 3. Deploy lần đầu

```bash
./scripts/deploy.sh
```

Script sẽ:
1. Validate `.env.prod`.
2. `git pull --ff-only`.
3. `docker compose --env-file .env.prod up -d --build`.
4. In trạng thái container + 30 dòng log cuối.

Kiểm tra:
```bash
docker compose --env-file .env.prod ps
docker compose --env-file .env.prod logs -f app
```

---

## 4. Nginx + HTTPS

### Cấu hình reverse proxy

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
sudo tee /etc/nginx/sites-available/kindergarten > /dev/null <<'NGINX'
server {
    server_name your-domain.com;

    client_max_body_size 500M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /minio/ {
        proxy_pass http://127.0.0.1:9001/;
        proxy_set_header Host $host;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        chunked_transfer_encoding off;
    }
}
NGINX

sudo ln -sf /etc/nginx/sites-available/kindergarten /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d your-domain.com
```

### Firewall

```bash
sudo ufw default deny incoming
sudo ufw allow 22/tcp     # SSH
sudo ufw allow 80/tcp     # HTTP (Certbot renewal)
sudo ufw allow 443/tcp    # HTTPS
sudo ufw allow 9000/tcp   # MinIO data (public read)
sudo ufw enable
```

Các port 3306 / 6379 / 9001 / 8080 đã bind `127.0.0.1` trong [docker-compose.yml](docker-compose.yml), không ra internet.

---

## 5. Deploy code mới

### Cách 1 — SSH thủ công (khuyến nghị ban đầu)

```bash
ssh deploy@your-domain.com
cd /srv/kindergarten-warehouse
./scripts/deploy.sh
```

### Cách 2 — GitHub Actions (auto-deploy)

1. Trong repo → **Settings → Secrets and variables → Actions → New secret**:

   | Secret | Giá trị |
   |--------|---------|
   | `SSH_HOST` | IP hoặc domain VPS |
   | `SSH_PORT` | `22` |
   | `SSH_USER` | `deploy` |
   | `SSH_PRIVATE_KEY` | nội dung file `~/.ssh/id_ed25519` private key của máy cá nhân, đã được authorize SSH vào VPS |
   | `DEPLOY_PATH` | `/srv/kindergarten-warehouse` |

2. Workflow [.github/workflows/deploy-prod.yml](.github/workflows/deploy-prod.yml) sẽ tự chạy khi push vào `main`, hoặc trigger thủ công qua tab Actions.

> GitHub Actions chỉ chứa **credential SSH**. Toàn bộ secret của app vẫn nằm trong `.env.prod` trên VPS.

---

## 6. Rotate secrets (định kỳ / sau incident)

1. SSH vào VPS.
2. Sinh giá trị mới:
   ```bash
   ./scripts/generate-secrets.sh
   ```
3. Sửa `.env.prod`, thay giá trị cần rotate. Ví dụ chỉ đổi `JWT_SECRET`:
   ```bash
   nano .env.prod   # thay JWT_SECRET=<giá trị mới>
   ```
4. Validate:
   ```bash
   ./scripts/check-env.sh .env.prod
   ```
5. Redeploy:
   ```bash
   ./scripts/deploy.sh
   ```

**Tác động rotate**:

| Biến thay đổi | Hậu quả |
|---------------|---------|
| `JWT_SECRET` | Toàn bộ user phải login lại |
| `MYSQL_PASSWORD` / `_ROOT_` | Cần update cả trong DB (`ALTER USER`) hoặc `docker compose down -v` để init lại |
| `REDIS_PASSWORD` | App + redis container đều phải restart |
| `MINIO_ROOT_PASSWORD` | MinIO container restart |
| `APP_ADMIN_PASSWORD` | **Không ảnh hưởng** — seed chỉ chạy lần đầu. Xem [docs/ADMIN_PASSWORD_RESET.md](docs/ADMIN_PASSWORD_RESET.md) |

---

## 7. Backup

```bash
# Full dump DB
docker exec warehouse_mysql sh -c \
  'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --routines --triggers --databases warehouse_db' \
  > /srv/backups/db_$(date +%F).sql

# Nén MinIO data
tar -czf /srv/backups/minio_$(date +%F).tar.gz \
  -C /var/lib/docker/volumes/kindergarten-warehouse_minio_data _data
```

Thiết lập cron hằng ngày:
```bash
crontab -e
# Thêm:
0 2 * * * /srv/kindergarten-warehouse/scripts/backup.sh >> /var/log/kindergarten-backup.log 2>&1
```

> Chưa có `scripts/backup.sh`? Tạo từ 2 lệnh ở trên hoặc yêu cầu bổ sung.

---

## 8. Kiểm tra nhanh sau deploy

| Check | Lệnh | Kỳ vọng |
|-------|------|---------|
| Containers healthy | `docker compose --env-file .env.prod ps` | Tất cả `running`/`healthy` |
| App log không panic | `docker compose --env-file .env.prod logs --tail 100 app` | Không có `ERROR` lặp |
| API sống | `curl https://your-domain.com/api/v1/auth/login -I` | HTTP 405 (method not allowed) hoặc 400 |
| Swagger | `curl https://your-domain.com/swagger-ui.html` | HTTP 200 |
| MinIO sống | `curl https://your-domain.com/minio/health/live` | HTTP 200 |

---

## 9. Rollback

Dùng tag hoặc commit hash:
```bash
cd /srv/kindergarten-warehouse
git fetch --tags
git checkout <tag-or-commit>
SKIP_GIT_PULL=1 ./scripts/deploy.sh
```

---

## 10. Security checklist (production)

- [ ] `.env.prod` có `chmod 600`, owner `deploy`.
- [ ] `JWT_SECRET` độ dài ≥ 32 (`./scripts/check-env.sh` xác nhận).
- [ ] `APP_ADMIN_PASSWORD` ≠ `admin123` và không nằm trong git history.
- [ ] Firewall chỉ mở 22, 80, 443, 9000.
- [ ] HTTPS đã cài (Certbot auto-renew chạy hằng tháng).
- [ ] `COOKIE_SECURE=true` trong `.env.prod`.
- [ ] `MAIL_PASSWORD` là Gmail App Password, không phải mật khẩu tài khoản.
- [ ] Không có file `.env*` (trừ `.env.example`) trong `git ls-files`.
- [ ] Admin đã đổi mật khẩu seed qua `/api/v1/auth/forgot-password` sau lần login đầu.
- [ ] Backup DB + MinIO volumes hằng ngày.
