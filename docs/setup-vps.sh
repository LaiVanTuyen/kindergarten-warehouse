#!/bin/bash
# =====================================================================
# VPS Setup Script - Kindergarten Warehouse
# Chạy script này trên Ubuntu 22.04 LTS mới tinh sau khi SSH vào Droplet
# Sử dụng: chmod +x setup-vps.sh && sudo ./setup-vps.sh
# =====================================================================

set -e  # Dừng ngay nếu có lỗi

echo "=================================================="
echo "  Kindergarten Warehouse - VPS Setup Script"
echo "=================================================="

# ---- BƯỚC 1: Update hệ thống ----
echo "[1/6] Updating system packages..."
apt-get update -y && apt-get upgrade -y

# ---- BƯỚC 2: Cài đặt Docker ----
echo "[2/6] Installing Docker..."
apt-get install -y ca-certificates curl gnupg lsb-release
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Thêm user vào group docker (tránh phải dùng sudo mỗi lần)
usermod -aG docker $SUDO_USER || true

echo "Docker installed: $(docker --version)"

# ---- BƯỚC 3: Cài đặt Nginx ----
echo "[3/6] Installing Nginx..."
apt-get install -y nginx

# ---- BƯỚC 4: Cài đặt Certbot (Let's Encrypt SSL) ----
echo "[4/6] Installing Certbot for SSL..."
apt-get install -y snapd
snap install --classic certbot
ln -s /snap/bin/certbot /usr/bin/certbot || true

# ---- BƯỚC 5: Tạo thư mục ứng dụng ----
echo "[5/6] Creating application directory at /opt/kindergarten-warehouse..."
mkdir -p /opt/kindergarten-warehouse
echo "  --> Bạn cần upload file .env và docker-compose.prod.yml vào đây!"

# ---- BƯỚC 6: Cấu hình Firewall ----
echo "[6/6] Configuring UFW Firewall..."
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

echo ""
echo "=================================================="
echo "  ✅ Setup hoàn tất! Các bước tiếp theo:"
echo "=================================================="
echo ""
echo "  1. Upload file cấu hình lên server:"
echo "     scp .env ubuntu@YOUR_IP:/opt/kindergarten-warehouse/"
echo "     scp docker-compose.prod.yml ubuntu@YOUR_IP:/opt/kindergarten-warehouse/"
echo "     scp docs/nginx.conf ubuntu@YOUR_IP:/tmp/warehouse.conf"
echo ""
echo "  2. Cấu hình Nginx:"
echo "     sudo mv /tmp/warehouse.conf /etc/nginx/sites-available/warehouse"
echo "     sudo ln -s /etc/nginx/sites-available/warehouse /etc/nginx/sites-enabled/"
echo "     sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "  3. Lấy chứng chỉ SSL (thay yourdomain.com bằng domain thật):"
echo "     sudo certbot --nginx -d api.yourdomain.com -d cdn.yourdomain.com"
echo ""
echo "  4. Kéo và chạy ứng dụng:"
echo "     cd /opt/kindergarten-warehouse"
echo "     docker compose -f docker-compose.prod.yml pull"
echo "     docker compose -f docker-compose.prod.yml up -d"
echo ""
echo "  5. Thêm SSH Secrets vào GitHub để kích hoạt Auto-Deploy:"
echo "     HOST=YOUR_DROPLET_IP"
echo "     USERNAME=ubuntu  (hoặc root)"
echo "     SSH_PRIVATE_KEY=<nội dung file ~/.ssh/id_rsa của máy local>"
echo ""
