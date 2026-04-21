#!/usr/bin/env bash
# ============================================================
# check-env.sh
# Kiểm tra file env trước khi deploy prod.
# ============================================================
# Dùng:
#   ./scripts/check-env.sh .env.prod
# Exit code:
#   0 = OK, 1 = validation failed, 2 = missing file/dep
# ============================================================
set -uo pipefail

ENV_FILE="${1:-.env.prod}"

# --- Helpers ---
c_red="\033[0;31m"; c_green="\033[0;32m"; c_yellow="\033[0;33m"; c_reset="\033[0m"
pass=0; fail=0

ok()   { echo -e "${c_green}✓${c_reset} $*"; pass=$((pass+1)); }
err()  { echo -e "${c_red}✗${c_reset} $*"; fail=$((fail+1)); }
warn() { echo -e "${c_yellow}!${c_reset} $*"; }

if [[ ! -f "$ENV_FILE" ]]; then
    echo "File not found: $ENV_FILE" >&2
    exit 2
fi

# --- Permission check (POSIX only) ---
if command -v stat >/dev/null 2>&1; then
    # macOS vs Linux stat có cú pháp khác — thử cả hai
    perm=$(stat -c "%a" "$ENV_FILE" 2>/dev/null || stat -f "%Lp" "$ENV_FILE" 2>/dev/null || echo "")
    if [[ -n "$perm" ]]; then
        if [[ "$perm" -gt 600 ]]; then
            err "Permission $perm là quá rộng. Chạy: chmod 600 $ENV_FILE"
        else
            ok "Permission $perm OK (≤ 600)"
        fi
    fi
fi

# --- Load env ---
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# --- Required vars ---
REQUIRED=(
    SPRING_PROFILES_ACTIVE
    DOMAIN_NAME
    MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
    REDIS_PASSWORD
    MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_BUCKET_NAME
    JWT_SECRET
    APP_ADMIN_USERNAME APP_ADMIN_EMAIL APP_ADMIN_PASSWORD
    MAIL_USERNAME MAIL_PASSWORD
    CORS_ALLOWED_ORIGINS
)

for v in "${REQUIRED[@]}"; do
    val="${!v:-}"
    if [[ -z "$val" ]]; then
        err "Missing or empty: $v"
    else
        ok "$v is set"
    fi
done

# --- Quality checks ---
weak_values=("change-me" "changeme" "admin123" "password" "123456" "replace-with-strong-password" "replace-with-strong-256-bit-secret")
for v in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD MINIO_ROOT_PASSWORD APP_ADMIN_PASSWORD JWT_SECRET; do
    val="${!v:-}"
    for weak in "${weak_values[@]}"; do
        if [[ "$val" == "$weak" ]]; then
            err "$v đang dùng giá trị YẾU/placeholder: '$val'"
        fi
    done
done

# JWT secret độ dài
jwt_len=${#JWT_SECRET}
if [[ $jwt_len -lt 32 ]]; then
    err "JWT_SECRET chỉ có $jwt_len ký tự — cần ≥ 32 (HS256 yêu cầu 256-bit)"
else
    ok "JWT_SECRET length = $jwt_len (≥ 32)"
fi

# Profile warn
if [[ "${SPRING_PROFILES_ACTIVE:-}" != "prod" ]]; then
    warn "SPRING_PROFILES_ACTIVE = '${SPRING_PROFILES_ACTIVE:-}' (không phải prod)"
fi

# Admin password == 'admin123' sẽ bị DataSeeder chặn ở profile prod
if [[ "${APP_ADMIN_PASSWORD:-}" == "admin123" ]]; then
    err "APP_ADMIN_PASSWORD = 'admin123' — DataSeeder sẽ crash ở profile=prod"
fi

# Cookie warn cho HTTPS
if [[ "${SPRING_PROFILES_ACTIVE:-}" == "prod" && "${COOKIE_SECURE:-false}" != "true" ]]; then
    warn "COOKIE_SECURE != true ở prod — JWT cookie sẽ đi qua HTTP không mã hóa"
fi

# --- Summary ---
echo ""
echo "====================================="
echo "Passed: $pass | Failed: $fail"
echo "====================================="

[[ $fail -eq 0 ]] || exit 1
