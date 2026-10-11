#!/usr/bin/env bash
# ============================================================
# backup-trial.sh — sao lưu MySQL + kho tệp MinIO của bản dùng thử
# ============================================================
# Tạo  $BACKUP_DIR/<thời-điểm>/{mysql.sql.gz,minio.tar.gz}  và xoá bản cũ hơn
# $KEEP_DAYS ngày. Chạy hằng đêm bằng cron:
#   15 2 * * *  cd /srv/kw/kindergarten-warehouse && ./scripts/backup-trial.sh >> /var/log/kw-backup.log 2>&1
#
# Một bản sao lưu nằm cùng máy chủ KHÔNG bảo vệ được khi máy bị thu hồi/hỏng:
# định kỳ chép thư mục này ra ngoài (máy của bạn, Google Drive, ...).
#
# Khôi phục (xem docs/TRIAL_DEPLOYMENT.md):
#   gunzip -c mysql.sql.gz | docker exec -i ${STACK_PREFIX}_mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
#   docker run --rm -v ${PROJECT}_minio_data:/data -v "$PWD":/b alpine tar xzf /b/minio.tar.gz -C /data
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.trial}"
[ -f "$ENV_FILE" ] || { echo "Thiếu $ENV_FILE" >&2; exit 2; }
# shellcheck disable=SC1090
set -a; . "./$ENV_FILE"; set +a

PROJECT="${PROJECT:-warehouse_trial}"
BACKUP_DIR="${BACKUP_DIR:-/srv/kw-backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_DIR/$STAMP"
mkdir -p "$TARGET"

echo "[$(date -Iseconds)] MySQL → $TARGET/mysql.sql.gz"
# --single-transaction: bản chụp nhất quán của InnoDB mà không khoá bảng khi giáo viên đang dùng.
docker exec "${STACK_PREFIX}_mysql" sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --databases "$MYSQL_DATABASE"' \
  | gzip > "$TARGET/mysql.sql.gz"

echo "[$(date -Iseconds)] MinIO → $TARGET/minio.tar.gz"
VOLUME="$(docker volume ls --format '{{.Name}}' | grep -E "^${PROJECT}_minio" | head -1)"
[ -n "$VOLUME" ] || { echo "Không tìm thấy volume MinIO của project $PROJECT" >&2; exit 1; }
docker run --rm -v "$VOLUME":/data:ro -v "$TARGET":/b alpine tar czf /b/minio.tar.gz -C /data .

# Một bản dump rỗng/hỏng nhìn vẫn như thành công — kiểm tra trước khi xoá bản cũ.
gzip -t "$TARGET/mysql.sql.gz"
[ "$(gzip -dc "$TARGET/mysql.sql.gz" | head -c 200000 | grep -c 'CREATE TABLE' || true)" -gt 0 ] \
  || { echo "Bản dump MySQL không có bảng nào — giữ lại bản cũ" >&2; exit 1; }

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$KEEP_DAYS" -exec rm -rf {} +
echo "[$(date -Iseconds)] Xong: $(du -sh "$TARGET" | cut -f1)"
