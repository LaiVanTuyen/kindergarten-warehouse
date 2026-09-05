#!/usr/bin/env bash
# Guard chống N+1 và chống kéo dữ liệu người dùng vào list/search Portal.
# Exit: 0 đạt, 1 regression, 2 môi trường/dataset chưa sẵn sàng.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${BASE_URL:-http://127.0.0.1:4301}"
MYSQL_CT="${MYSQL_CONTAINER:-warehouse_demo_mysql}"
MAX_QUERIES="${MAX_QUERIES:-8}"
MAX_DELTA="${MAX_DELTA:-1}"
CURL=(curl -sS --connect-timeout 5 --max-time 20)

MYSQL_PW=$(grep -m1 '^MYSQL_ROOT_PASSWORD=' "$REPO_DIR/.env.demo" | cut -d= -f2-) || exit 2
fail=0
ok() { echo "[OK] $*"; }
bad() { echo "[LOI] $*"; fail=1; }
mysql() { docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" "$@" 2>/dev/null; }

probe=$(mktemp)
trap 'rm -f "$probe"' EXIT
meta=$("${CURL[@]}" -o "$probe" -w '%{http_code}|%{content_type}' \
  "$BASE/api/v1/resources?page=0&size=1") || exit 2
[ "${meta%%|*}" = 200 ] || { echo "Endpoint chưa sẵn sàng: $meta"; exit 2; }
case "${meta#*|}" in application/json*) ;; *) echo "Response không phải JSON"; exit 2;; esac
grep -q '"content"' "$probe" || { echo "Body thiếu result.content"; exit 2; }
total=$(grep -o '"totalElements":[0-9]*' "$probe" | head -1 | cut -d: -f2)
[ "${total:-0}" -gt 0 ] || { echo "Dataset rỗng"; exit 2; }
"${CURL[@]}" "$BASE/api/v1/resources?page=0&size=1&keyword=0042" \
  | grep -q 'perf-seed-0042' || { echo "Sai dataset perf (thiếu marker)"; exit 2; }

select_count() {
  mysql -N -e "SHOW GLOBAL STATUS LIKE 'Com_select';" | awk '{print $2}'
}
request_queries() {
  local path="$1" before after
  before=$(select_count) || exit 2
  "${CURL[@]}" -o /dev/null "$BASE$path" || exit 2
  after=$(select_count) || exit 2
  echo $((after-before-1))
}
check_sizes() {
  local suffix="$1" q1 q12 q50 d12 d50
  q1=$(request_queries "/api/v1/resources?page=0&size=1$suffix")
  q12=$(request_queries "/api/v1/resources?page=0&size=12$suffix")
  q50=$(request_queries "/api/v1/resources?page=0&size=50$suffix")
  d12=$((q12>q1 ? q12-q1 : q1-q12)); d50=$((q50>q1 ? q50-q1 : q1-q50))
  [ "$q1" -le "$MAX_QUERIES" ] && [ "$d12" -le "$MAX_DELTA" ] && [ "$d50" -le "$MAX_DELTA" ] \
    && ok "query ổn định: $q1/$q12/$q50" \
    || bad "nghi N+1: $q1/$q12/$q50"
}

"${CURL[@]}" -o /dev/null "$BASE/api/v1/resources?page=0&size=12"
check_sizes ""
check_sizes "&keyword=0042"

mysql -e "TRUNCATE performance_schema.events_statements_summary_by_digest;" || exit 2
"${CURL[@]}" -o /dev/null "$BASE/api/v1/resources?page=0&size=12"
n_users=$(mysql -N -e "SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='warehouse_demo_db' AND DIGEST_TEXT LIKE '%users%';")
n_secret=$(mysql -N -e "SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='warehouse_demo_db' AND (DIGEST_TEXT LIKE '%password%' OR DIGEST_TEXT LIKE '%token_version%' OR DIGEST_TEXT LIKE '%original_email%');")
n_fav=$(mysql -N -e "SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='warehouse_demo_db' AND DIGEST_TEXT LIKE '%favorites%';")
[ "${n_users:-1}" -eq 0 ] && ok "không query bảng users" || bad "có $n_users query bảng users"
[ "${n_secret:-1}" -eq 0 ] && ok "không select cột nhạy cảm" || bad "có $n_secret query cột nhạy cảm"
[ "${n_fav:-1}" -eq 0 ] && ok "guest không query favorites" || bad "guest có $n_fav query favorites"

exit "$fail"
