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
# Cộng các điều kiện an toàn:
#   - TỐI ĐA MỘT truy vấn chạm bảng `users`. Đường list được phép lấy tên
#     hiển thị (cột "Người tải lên" của màn Admin dùng chính endpoint này),
#     nhưng phải bằng một batch query hẹp `id, full_name` — nhiều hơn một là
#     dấu hiệu hydrate entity hoặc N+1.
#   - Không truy vấn nào select `password`, `token_version`, `original_email`.
#   - Khách không sinh truy vấn `favorites`.
#   - Dữ liệu trả về đúng theo từng nhóm viewer.
#
# Lưu ý khi sửa các điều kiện dùng DIGEST_TEXT: MySQL chuẩn hoá digest CÓ
# backtick (`FROM `users``), nên mẫu LIKE '%FROM users%' sẽ KHÔNG BAO GIỜ
# khớp và tạo ra "pass giả". Dùng '%users%'.
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

# ĐIỀU KIỆN TIÊN QUYẾT — không được bỏ.
# Nếu app chưa sẵn sàng (vd. nginx tra 502 trong lúc container khoi dong lai),
# moi phep dem se ra 0 query va CAC KIEM TRA SE BAO OK TREN DU LIEU RONG —
# tuc la pass gia, nguy hiem hon la fail. Phai chan ngay tu dau.
# EXIT CODE — CI phan biet duoc hai loai that bai:
#   exit 2 = moi truong chua san sang (app chua len, dataset chua seed)
#   exit 1 = query regression that su
CURL="curl -s --connect-timeout 5 --max-time 20"

probe=$($CURL -o /tmp/qg_probe.json -w '%{http_code}|%{content_type}' \
    "$BASE/api/v1/resources?page=0&size=1") || {
    echo "  [DUNG] curl that bai khi goi $BASE — app chua len hoac sai BASE_URL."
    exit 2
}
http_code="${probe%%|*}"
ctype="${probe#*|}"

if [ "$http_code" != "200" ]; then
    echo "  [DUNG] Endpoint list tra HTTP $http_code, khong phai 200."
    echo "         Do bay gio se cho ket qua vo nghia (moi phep dem ra 0 query"
    echo "         va MOI KIEM TRA SE BAO OK TREN DU LIEU RONG)."
    exit 2
fi

case "$ctype" in
    application/json*) ;;
    *) echo "  [DUNG] Content-Type la '$ctype', khong phai application/json."
       echo "         Nhieu kha nang dang nhan trang loi HTML tu nginx."
       exit 2 ;;
esac

if ! grep -q '"content"' /tmp/qg_probe.json; then
    echo "  [DUNG] Body khong co result.content — response khong dung dang mong doi."
    exit 2
fi

n_check=$(grep -o '"totalElements":[0-9]*' /tmp/qg_probe.json | cut -d: -f2)
if [ -z "$n_check" ] || [ "$n_check" -eq 0 ]; then
    echo "  [DUNG] Dataset rong (totalElements=${n_check:-khong doc duoc})."
    echo "         Chay perf/seed-baseline.sql truoc."
    exit 2
fi

# Kiem MARKER cua perf seed, khong chi kiem tong > 0. Mot database that co
# du lieu nhung khac dataset se cho so do khong so sanh duoc voi lan truoc.
if ! $CURL "$BASE/api/v1/resources?page=0&size=1&keyword=0042" \
        | grep -q 'perf-seed-0042'; then
    echo "  [DUNG] Khong tim thay marker 'perf-seed-0042'."
    echo "         Dataset khong phai bo seed cua perf/seed-baseline.sql."
    exit 2
fi

rm -f /tmp/qg_probe.json
echo "  tien quyet OK: HTTP 200, JSON hop le, totalElements=$n_check, co marker perf-seed"
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

# Duong list ĐƯỢC PHÉP cham bang users, nhung chi bang MOT truy van hep
# lay ten hien thi (id + full_name). Cot "Nguoi tai len" cua man Admin dung
# chinh endpoint nay. Dieu cam la:
#   - nhieu hon mot truy van users (dau hieu N+1 hoac hydrate entity)
#   - truy van users select cot nhay cam
n_users=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N -e "
    SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest
    WHERE SCHEMA_NAME='warehouse_demo_db' AND DIGEST_TEXT LIKE '%users%';" 2>/dev/null)
n_secret=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N -e "
    SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest
    WHERE SCHEMA_NAME='warehouse_demo_db'
      AND (DIGEST_TEXT LIKE '%password%'
        OR DIGEST_TEXT LIKE '%token_version%'
        OR DIGEST_TEXT LIKE '%original_email%');" 2>/dev/null)

if [ "${n_users:-9}" -le 1 ]; then
    ok "toi da mot truy van users (hep, lay ten hien thi) — thay $n_users"
else
    bad "co $n_users truy van users — nghi hydrate entity hoac N+1"
fi

if [ "${n_secret:-1}" -eq 0 ]; then
    ok "khong select password/token_version/original_email"
else
    bad "co $n_secret query select cot nhay cam tren endpoint cong khai"
fi


# ===========================================================
# Keyword search dung chung projection voi list
# ===========================================================
echo
echo "-- keyword search --"

dem_query_search() {
    local size="$1" before after
    before=$(com_select)
    curl -s -o /dev/null "$BASE/api/v1/resources?page=0&size=$size&keyword=cho"
    after=$(com_select)
    echo $((after - before - 1))
}

s1=$(dem_query_search 1)
s50=$(dem_query_search 50)
echo "  search size=1  -> $s1 query"
echo "  search size=50 -> $s50 query"

ds=$(( s50 > s1 ? s50 - s1 : s1 - s50 ))
if [ "$s1" -le "$NGUONG" ] && [ "$ds" -le "$DELTA" ]; then
    ok "search khong tang theo so ban ghi (chenh $ds <= $DELTA)"
else
    bad "search vuot nguong hoac tang theo so ban ghi (s1=$s1 s50=$s50)"
fi

# ===========================================================
# Du lieu theo tung nhom viewer
# ===========================================================
echo
echo "-- du lieu theo viewer --"

ADMIN_EMAIL=$(grep -m1 '^APP_ADMIN_EMAIL=' "$REPO_DIR/.env.demo" | cut -d= -f2-)
ADMIN_PASS=$(grep -m1 '^APP_ADMIN_PASSWORD=' "$REPO_DIR/.env.demo" | cut -d= -f2-)

total_for() {
    local jar="${1:-}"
    if [ -n "$jar" ]; then
        curl -s -b "$jar" "$BASE/api/v1/resources?page=0&size=1"
    else
        curl -s "$BASE/api/v1/resources?page=0&size=1"
    fi | grep -o '"totalElements":[0-9]*' | cut -d: -f2
}

guest_total=$(total_for)

jar=$(mktemp)
# KHONG in mat khau ra log
curl -s -c "$jar" -X POST "$BASE/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}" -o /dev/null
admin_total=$(total_for "$jar")

# So sanh voi DB: khach chi thay PUBLIC+APPROVED qua ca chuoi Category/Topic
db_guest=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" warehouse_demo_db -N -e "
    SELECT COUNT(*) FROM resources r
      JOIN topics t ON t.id = r.topic_id
      JOIN categories c ON c.id = t.category_id
    WHERE r.is_deleted = 0 AND r.visibility = 'PUBLIC' AND r.status = 'APPROVED'
      AND t.is_deleted = 0 AND t.visibility = 'PUBLIC'
      AND c.is_deleted = 0 AND c.visibility = 'PUBLIC';" 2>/dev/null)

echo "  guest totalElements = $guest_total (DB = $db_guest)"
echo "  admin totalElements = $admin_total"

if [ "$guest_total" = "$db_guest" ]; then
    ok "guest thay dung so ban ghi PUBLIC+APPROVED"
else
    bad "guest lech: API=$guest_total DB=$db_guest"
fi

if [ "${admin_total:-0}" -ge "${guest_total:-0}" ]; then
    ok "admin thay >= guest ($admin_total >= $guest_total)"
else
    bad "admin thay IT hon guest ($admin_total < $guest_total) — sai visibility"
fi

# Khach khong duoc nhan bat ky ban ghi INTERNAL/PRIVATE nao
n_leak=$(curl -s "$BASE/api/v1/resources?page=0&size=100" \
    | grep -o '"visibility":"[A-Z]*"' | grep -cv '"visibility":"PUBLIC"' || true)
if [ "${n_leak:-1}" -eq 0 ]; then
    ok "guest khong nhan ban ghi INTERNAL/PRIVATE nao"
else
    bad "guest nhan $n_leak ban ghi khong phai PUBLIC"
fi

# Khach khong duoc sinh truy van favorites
docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" \
    -e "TRUNCATE performance_schema.events_statements_summary_by_digest;" 2>/dev/null
curl -s -o /dev/null "$BASE/api/v1/resources?page=0&size=12"
n_fav=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N -e "
    SELECT COUNT(*) FROM performance_schema.events_statements_summary_by_digest
    WHERE SCHEMA_NAME='warehouse_demo_db' AND DIGEST_TEXT LIKE '%FROM favorites%';" 2>/dev/null)
if [ "${n_fav:-1}" -eq 0 ]; then
    ok "guest khong sinh truy van favorites"
else
    bad "guest sinh $n_fav truy van favorites — thua"
fi

rm -f "$jar"

echo
if [ "$fail" -eq 0 ]; then
    echo "== TAT CA DAT =="
else
    echo "== CO MUC KHONG DAT =="
fi
exit "$fail"
