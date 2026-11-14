#!/usr/bin/env bash
# =====================================================================
# 维度8 STAB-04：binlog 增量备份（每小时）
# crontab：0 * * * * bash /path/to/fssc/scripts/backup-incremental.sh >> logs/backup-inc.log 2>&1
# 前置：MySQL log_bin=ON；恢复时用 mysqlbinlog --start-position 做 PITR
# =====================================================================
set -uo pipefail
cd "$(dirname "$0")/.."
DB_HOST=192.168.20.77; DB_PORT=3306; DB_USER=root; DB_PASS="${FSSC_DB_PASSWORD:-123456}"
INC_DIR="backup/binlog/$(date +%Y%m)"
mkdir -p "$INC_DIR"

# 获取当前 binlog 文件清单并刷新
FILES=$("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -e "FLUSH BINARY LOGS; SHOW BINARY LOGS;" 2>/dev/null | awk '{print $1}')
# 拷贝除当前活动 binlog 外的所有文件（幂等，已存在跳过）
LAST=$(echo "$FILES" | tail -1)
for f in $FILES; do
  [ "$f" = "$LAST" ] && continue
  SRC=$("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -e "SELECT @@log_bin_basename" 2>/dev/null)
  DIR=$(dirname "$SRC")
  if [ -f "$DIR/$f" ] && [ ! -f "$INC_DIR/$f" ]; then
    cp "$DIR/$f" "$INC_DIR/$f" && echo "[$(date '+%F %T')] 已增量备份 $f"
  fi
done
echo "[$(date '+%F %T')] binlog 增量备份完成 → $INC_DIR"
