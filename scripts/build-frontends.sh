#!/usr/bin/env bash
# ============================================================
# build-frontends.sh — build Portal + Admin (cấu hình demo, same-origin /api)
# ============================================================
# Chạy trong container Node nên máy chủ không cần cài Node; chạy được trên ARM
# (Oracle Ampere A1) lẫn x86. Kết quả: ../kindergarten-warehouse-angular/dist/apps/{portal,admin}/browser
#
#   ./scripts/build-frontends.sh [đường-dẫn-repo-angular]
# ============================================================
set -euo pipefail

ANGULAR_DIR="$(cd "${1:-$(dirname "$0")/../../kindergarten-warehouse-angular}" && pwd)"
[ -f "$ANGULAR_DIR/package.json" ] || { echo "Không thấy repo Angular ở $ANGULAR_DIR" >&2; exit 2; }

echo ">> Build Angular trong node:22 tại $ANGULAR_DIR"
docker run --rm \
  -v "$ANGULAR_DIR":/workspace \
  -v warehouse_npm_cache:/root/.npm \
  -w /workspace \
  node:22-bookworm \
  bash -c "npm ci --no-audit --no-fund && npx nx run-many -t build -p portal admin -c demo --skip-nx-cache"

for app in portal admin; do
  test -f "$ANGULAR_DIR/dist/apps/$app/browser/index.html" || { echo "Thiếu dist của $app" >&2; exit 1; }
done
echo ">> Xong. Khởi động lại nginx để chắc chắn phục vụ bản mới:"
echo "   docker compose -p warehouse_trial restart portal admin-ui"
