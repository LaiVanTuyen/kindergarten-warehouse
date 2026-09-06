#!/usr/bin/env bash
# ===========================================================
# run-baseline.sh — chạy baseline k6 trên stack DEMO
# ===========================================================
#   ./perf/run-baseline.sh [thư-mục-kết-quả]
#
# Điều kiện đo phải cố định để Tuần 7 so sánh được:
#   - Cùng máy, cùng cấu hình Docker, stack demo đang chạy
#   - Dataset từ perf/seed-baseline.sql (2.000 resources)
#   - Warm-up trước mỗi bài đo, phần warm-up KHÔNG tính vào kết quả
#   - k6 chạy trong cùng docker network, gọi thẳng nginx `portal`
#     → loại bỏ biến động của port-forward trên host
#
# Không trộn upload/email vào bài đọc. Download đo riêng.
# ===========================================================
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-$REPO_DIR/perf/results/$(date +%Y%m%d-%H%M%S)}"
NETWORK="warehouse_demo_warehouse-network"
K6_IMAGE="grafana/k6:latest"
DURATION="${DURATION:-60s}"
WARMUP="${WARMUP:-20s}"
VU_SCALE="${VU_SCALE:-1}"
# SAMPLE=1 bật lấy mẫu CPU. Nó làm giảm throughput rất nhiều — xem ghi chú dài
# ở sample_stats. Dùng SAMPLE=0 khi cần con số throughput thật.
SAMPLE="${SAMPLE:-1}"
SAMPLE_INTERVAL="${SAMPLE_INTERVAL:-5}"

ENV_FILE="$REPO_DIR/.env.demo"
LOGIN_EMAIL=$(grep -m1 '^APP_ADMIN_EMAIL=' "$ENV_FILE" | cut -d= -f2-)
LOGIN_PASSWORD=$(grep -m1 '^APP_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
MYSQL_PW=$(grep -m1 '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)

mkdir -p "$OUT_DIR"

echo "== Baseline k6 =="
echo "   commit    : $(git -C "$REPO_DIR" rev-parse --short HEAD)"
echo "   kết quả   : $OUT_DIR"
echo "   duration  : $DURATION (warm-up $WARMUP), VU_SCALE=$VU_SCALE"
echo

# --- Ghi lại bối cảnh để lần đo sau đối chiếu ---
{
    echo "commit=$(git -C "$REPO_DIR" rev-parse HEAD)"
    echo "commit_short=$(git -C "$REPO_DIR" rev-parse --short HEAD)"
    echo "ngay_do=$(date -Iseconds)"
    echo "duration=$DURATION"
    echo "warmup=$WARMUP"
    echo "vu_scale=$VU_SCALE"
    echo "sample=$SAMPLE (interval=${SAMPLE_INTERVAL}s)"
    echo "docker_version=$(docker version --format '{{.Server.Version}}')"
    echo "k6_image=$K6_IMAGE"
    echo "download_mode=stream (StreamingResponseBody qua Spring)"
} > "$OUT_DIR/context.txt"

docker exec warehouse_demo_mysql mysql -uroot -p"$MYSQL_PW" warehouse_demo_db \
    -e "SELECT COUNT(*) AS tong,
               SUM(visibility='PUBLIC' AND status='APPROVED') AS portal_thay
        FROM resources;" 2>/dev/null >> "$OUT_DIR/context.txt"

run_k6() {
    local script="$1" label="$2" dur="$3" tag="$4"
    # MSYS_NO_PATHCONV: Git Bash trên Windows sẽ bẻ "/scripts" thành đường dẫn
    # Windows nếu không tắt path conversion, làm k6 không thấy script.
    MSYS_NO_PATHCONV=1 docker run --rm \
        --network "$NETWORK" \
        -v "$REPO_DIR/perf/k6":/scripts:ro \
        -e BASE_URL=http://portal \
        -e DURATION="$dur" \
        -e VU_SCALE="$VU_SCALE" \
        -e LOGIN_EMAIL="$LOGIN_EMAIL" \
        -e LOGIN_PASSWORD="$LOGIN_PASSWORD" \
        "$K6_IMAGE" run \
        --summary-trend-stats "avg,min,med,p(50),p(95),p(99),max" \
        ${tag:+--summary-export=/scripts/../out.json} \
        "/scripts/$script" 2>&1 | tee "$OUT_DIR/$label.txt"
}

# Lấy mẫu CPU LÀM MÉO CHÍNH PHÉP ĐO.
#
# Bản đầu tiên chạy vòng lặp 70 lượt KHÔNG NGHỈ, mỗi lượt spawn
# `docker stats --no-stream` (tự nó mất 1-2 giây) và một `docker exec` vào
# MySQL. Trên máy có quota 8 lõi, việc đó tranh CPU trực tiếp với app đang đo.
#
# Đo ngày 2026-09-06, cùng build, cùng dataset, kịch bản public-read 350 VU:
#   không lấy mẫu : 222,4 và 218,4 req/s   (t_list P95 ~2,9 s)
#   có lấy mẫu    : 74,8 → 103,8 req/s     (t_list P95 6,3–7,6 s)
# Tức chi phí ~55 %, không phải ~18 % như ước tính cũ ở PERF_BASELINE §6c.4.
#
# Hệ quả: KHÔNG được đọc throughput từ lượt có lấy mẫu. Chạy hai lượt —
# SAMPLE=0 để lấy throughput/độ trễ, SAMPLE=1 để quy trách nhiệm CPU.
sample_stats() {
    local label="$1" seconds="$2"
    if [ "$SAMPLE" != "1" ]; then
        : > "$OUT_DIR/$label.stats.csv"
        echo ""
        return
    fi
    ( local elapsed=0
      while [ "$elapsed" -lt "$seconds" ]; do
        docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' \
            warehouse_demo_app warehouse_demo_mysql warehouse_demo_portal 2>/dev/null
        docker exec warehouse_demo_mysql mysql -uroot -p"$MYSQL_PW" -N \
            -e "SHOW STATUS LIKE 'Threads_connected';" 2>/dev/null \
            | awk '{print "db_threads_connected," $2}'
        sleep "$SAMPLE_INTERVAL"
        elapsed=$((elapsed + SAMPLE_INTERVAL))
      done ) > "$OUT_DIR/$label.stats.csv" 2>/dev/null &
    echo $!
}

for pair in "public-read.js:public-read" "auth-read.js:auth-read" "download.js:download"; do
    script="${pair%%:*}"; label="${pair##*:}"

    echo "--- [$label] warm-up $WARMUP (không tính vào kết quả) ---"
    DURATION="$WARMUP" run_k6 "$script" "$label.warmup" "$WARMUP" "" >/dev/null 2>&1

    echo "--- [$label] đo thật $DURATION ---"
    pid=$(sample_stats "$label" 70)
    run_k6 "$script" "$label" "$DURATION" ""
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    echo
done

echo "== Xong. Kết quả trong $OUT_DIR =="
ls -la "$OUT_DIR"
