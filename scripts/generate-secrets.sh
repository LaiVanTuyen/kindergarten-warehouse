#!/usr/bin/env bash
# ============================================================
# generate-secrets.sh
# Sinh các giá trị secret mạnh dùng cho .env.prod
# ============================================================
# Cách dùng:
#   ./scripts/generate-secrets.sh            # in ra stdout
#   ./scripts/generate-secrets.sh >> .env.prod.new   # ghi vào file mới
# ============================================================
set -euo pipefail

# --- Helpers ---
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1" >&2; exit 2; }; }
need openssl

rand_b64() {
    # $1 = số byte nguyên liệu (mỗi byte = ~1.33 ký tự base64)
    openssl rand -base64 "$1" | tr -d '\n='
}

rand_pass() {
    # Sinh password 24 ký tự, không có ký tự dễ nhầm (0, O, l, 1, I)
    local charset='abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789@#$%^&*_-'
    local len=24
    local result=""
    while [[ ${#result} -lt $len ]]; do
        local byte
        byte=$(openssl rand -hex 1)
        local idx=$(( 16#$byte % ${#charset} ))
        result+="${charset:$idx:1}"
    done
    echo "$result"
}

echo "# ---- Generated secrets $(date -Iseconds) ----"
echo "# Sao chép các dòng bên dưới vào .env.prod"
echo ""
echo "JWT_SECRET=$(rand_b64 48)"
echo "APP_ADMIN_PASSWORD=$(rand_pass)"
echo "MYSQL_ROOT_PASSWORD=$(rand_pass)"
echo "MYSQL_PASSWORD=$(rand_pass)"
echo "REDIS_PASSWORD=$(rand_pass)"
echo "MINIO_ROOT_PASSWORD=$(rand_pass)"
