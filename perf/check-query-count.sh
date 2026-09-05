#!/usr/bin/env bash
# ===========================================================
# check-query-count.sh — chống N+1 cho đường list Portal
# ===========================================================
#   bash perf/check-query-count.sh
#
# KHÔNG khoá con số tuyệt đối. Chỉ cần Hibernate đổi cách fetch một cách
# hợp lệ là test kiểu `assert queryCount == 6` sẽ vỡ mà chẳng có lỗi thật.
# Thay vào đó kiểm ba điều kiện:
#
#   1. page size 1  → số query <= NGUONG
#   2. page size 12 → chênh so với size 1 không quá DELTA
#   3. page size 50 → chênh so với size 1 không quá DELTA (không tuyến tính)
#
# Cộng thêm một điều kiện an toàn: đường list KHÔNG được sinh query nào
# chạm bảng `users`, và không được select `password`/`token_version`.
#
# --- Vì sao là script chứ không phải JUnit test ---
# Đếm SQL cần một database thật. Flyway migration của dự án viết cho MySQL
# nên không chạy trên H2, và thêm Testcontainers vào chỉ để đo query là quá
# nặng cho lúc này. Script này chạy trên stack demo đang chạy, tái lập được
# và đủ để chặn hồi quy trong CI nếu có sẵn stack.
# ===========================================================
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${BASE_URL:-http://127.0.0.1:4301}"
NGUONG="${NGUONG:-8}"   # trần số query cho một request list
DELTA="${DELTA:-1}"     # chênh lệch cho phép giữa các page size

MYSQL_PW=$(grep -m1 '^MYSQL_ROOT_PASSWORD=' "$REPO_DIR/.env.demo" | cut -d= -f2-)
MYSQL_CT="${MYSQL_CONTAINER:-warehouse_demo_mysql}"

fail=0
ok()  { echo "  [OK]   $*"; }
bad() { echo "  [LOI]  $*"; fail=1; }

com_select() {
    docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N \
        -e "SHOW GLOBAL STATUS LIKE 'Com_select';" 2>/dev/null | awk '{print $2}'
}

dem_query() {
    local size="$1" before after
    before=$(com_select)
    curl -s -o /dev/null "$BASE/api/v1/resources?page=0&size=$size"
    after=$(com_select)
    # trừ 1 cho chính câu SHOW GLOBAL STATUS ở giữa
    echo $((after - before - 1))
}

echo "== Kiem tra so query cua duong list Portal =="
echo "   nguong=$NGUONG  delta cho phep=$DELTA"
echo

# Làm nóng, tránh tính cả chi phí khởi tạo lần đầu
curl -s -o /dev/null "$BASE/api/v1/resources?page=0&size=12"

q1=$(dem_query 1)
q12=$(dem_query 12)
q50=$(dem_query 50)

echo "  size=1  -> $q1 query"
echo "  size=12 -> $q12 query"
echo "  size=50 -> $q50 query"
echo

# 1. trần
if [ "$q1" -le "$NGUONG" ]; then
    ok "size=1 nam trong nguong ($q1 <= $NGUONG)"
else
    bad "size=1 vuot nguong ($q1 > $NGUONG)"
fi

# 2 + 3. không tăng theo số bản ghi
d12=$(( q12 > q1 ? q12 - q1 : q1 - q12 ))
d50=$(( q50 > q1 ? q50 - q1 : q1 - q50 ))

if [ "$d12" -le "$DELTA" ]; then
    ok "size=12 khong tang theo so ban ghi (chenh $d12 <= $DELTA)"
else
    bad "size=12 tang theo so ban ghi (chenh $d12 > $DELTA) — nghi N+1"
fi

if [ "$d50" -le "$DELTA" ]; then
    ok "size=50 khong tang theo so ban ghi (chenh $d50 <= $DELTA)"
else
    bad "size=50 tang theo so ban ghi (chenh $d50 > $DELTA) — nghi N+1"
fi

# 4. an toan: khong duoc cham bang users tren duong list
docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" \
    -e "TRUNCATE performance_schema.events_statements_summary_by_digest;" 2>/dev/null
curl -s -o /dev/null "$BASE/api/v1/resources?page=0&size=12"

n_users=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N -e "
    SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest
    WHERE SCHEMA_NAME='warehouse_demo_db' AND DIGEST_TEXT LIKE '%users%';" 2>/dev/null)
n_secret=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N -e "
    SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest
    WHERE SCHEMA_NAME='warehouse_demo_db'
      AND (DIGEST_TEXT LIKE '%password%' OR DIGEST_TEXT LIKE '%token_version%');" 2>/dev/null)

if [ "${n_users:-1}" -eq 0 ]; then
    ok "khong co query nao cham bang users"
else
    bad "co $n_users query cham bang users — projection da bi keo lai vao entity?"
fi

if [ "${n_secret:-1}" -eq 0 ]; then
    ok "khong select password/token_version"
else
    bad "co $n_secret query select password/token_version tren endpoint cong khai"
fi

echo
if [ "$fail" -eq 0 ]; then
    echo "== TAT CA DAT =="
else
    echo "== CO MUC KHONG DAT =="
fi
exit "$fail"
