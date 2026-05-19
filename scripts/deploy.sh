#!/usr/bin/env bash
# ============================================================
# deploy.sh — Deploy script cho prod server
# ============================================================
# Chạy trên server (không phải local):
#   ./scripts/deploy.sh
#
# Tiến trình:
#   1. git pull
#   2. Validate .env.prod qua check-env.sh
#   3. docker compose --env-file .env.prod up -d --build
#   4. In log 20 dòng cuối
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env.prod"
COMPOSE_ARGS=(--env-file "$ENV_FILE")

# --- Pre-flight ---
if [[ ! -f "$ENV_FILE" ]]; then
    echo "❌ Không tìm thấy $ENV_FILE. Copy từ .env.example rồi điền giá trị thật." >&2
    exit 1
fi

echo "🔎 Validate $ENV_FILE …"
bash scripts/check-env.sh "$ENV_FILE"

# --- Pull latest code (bỏ qua nếu là CI deploy đã checkout sẵn) ---
if [[ "${SKIP_GIT_PULL:-0}" != "1" ]]; then
    echo "⬇️  git pull …"
    git pull --ff-only
fi

# --- Build & run ---
echo "🚀 docker compose build & up …"
docker compose "${COMPOSE_ARGS[@]}" pull || true
docker compose "${COMPOSE_ARGS[@]}" up -d --build

# --- Verify ---
sleep 5
echo ""
echo "📦 Containers:"
docker compose "${COMPOSE_ARGS[@]}" ps

echo ""
echo "📜 Last 30 log lines (app):"
docker compose "${COMPOSE_ARGS[@]}" logs --tail 30 app || true

echo ""
echo "✅ Deploy xong. Kiểm tra domain + /actuator/health nếu có."
