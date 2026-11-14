#!/usr/bin/env bash
# =====================================================================
# 维度8 STAB-04：每日定时全量备份 + 失败告警 + 自动恢复演练
# crontab：0 2 * * * bash /path/to/fssc/scripts/cron-backup.sh >> logs/backup.log 2>&1
# =====================================================================
set -uo pipefail
cd "$(dirname "$0")/.."
ALERT_WEBHOOK="${OPS_ALERT_WEBHOOK:-}"
DB_HOST=192.168.20.77; DB_PORT=3306; DB_USER=root; DB_PASS="${FSSC_DB_PASSWORD:-123456}"

alert() {
  echo "[$(date '+%F %T')] ALERT: $1"
  [ -n "$ALERT_WEBHOOK" ] && curl -sm 5 -X POST "$ALERT_WEBHOOK" \
    -H 'Content-Type: application/json' \
    -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"FSSC备份告警: $1\"}}" || true
}

echo "[$(date '+%F %T')] cron-backup 开始"
if bash scripts/backup-db.sh; then
  echo "[$(date '+%F %T')] 全量备份成功"
else
  alert "每日全量备份失败，请检查 MySQL 状态与磁盘空间"
  exit 1
fi

# 备份后自动恢复演练（保留演练库，不动生产）
LATEST=$(ls -t backup/*/fssc_*.sql.gz 2>/dev/null | head -1)
if [ -n "$LATEST" ]; then
  if bash scripts/restore-db.sh --dir "$(dirname "$LATEST")" --keep-drill; then
    echo "[$(date '+%F %T')] 恢复演练通过"
  else
    alert "备份恢复演练失败：$LATEST 不可恢复，立即排查"
  fi
else
  alert "未找到备份文件"
fi
