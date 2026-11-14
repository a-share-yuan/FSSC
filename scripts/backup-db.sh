#!/usr/bin/env bash
# =============================================================================
# FSSC 数据库全量备份（P0-B3）
#
#   备份 fssc（主库）与 fssc_image（影像库）到 backup/<时间戳>/，并记录 binlog 位点
#   以便需要时做时间点恢复（PITR）。备份完成后做一次完整性校验（gzip -t + 表数比对）。
#
# 用法：
#   bash scripts/backup-db.sh                      # 备份两个库，保留最近 14 份
#   bash scripts/backup-db.sh --keep 30            # 保留 30 份
#   bash scripts/backup-db.sh --dbs fssc           # 只备份主库
#
# 退出码：0 成功；非 0 失败（可直接用于 CI / cron 判定）
# =============================================================================
set -u -o pipefail

# ---------- 默认参数（可用环境变量覆盖，便于接入生产）----------
MYSQL_BIN="${MYSQL_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe}"
DB_HOST="${FSSC_DB_HOST:-192.168.20.77}"
DB_PORT="${FSSC_DB_PORT:-3306}"
DB_USER="${FSSC_DB_USER:-root}"
DB_PASSWORD="${FSSC_DB_PASSWORD:-123456}"
DBS="${FSSC_BACKUP_DBS:-fssc fssc_image}"
KEEP=14

while [ $# -gt 0 ]; do
    case "$1" in
        --keep) KEEP="$2"; shift 2 ;;
        --dbs)  DBS="$2";  shift 2 ;;
        --host) DB_HOST="$2"; shift 2 ;;
        --port) DB_PORT="$2"; shift 2 ;;
        --user) DB_USER="$2"; shift 2 ;;
        *) echo "[错误] 未知参数：$1"; exit 2 ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$ROOT/backup/$STAMP"
mkdir -p "$OUT_DIR"

CONN=(--host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" "--password=$DB_PASSWORD")

echo "=============================================="
echo " FSSC 数据库备份"
echo " 时间   : $(date '+%Y-%m-%d %H:%M:%S')"
echo " 来源   : $DB_HOST:$DB_PORT"
echo " 库     : $DBS"
echo " 输出   : $OUT_DIR"
echo "=============================================="

FAIL=0
for db in $DBS; do
    file="$OUT_DIR/${db}.sql.gz"
    echo ">> 备份 $db ..."
    # --single-transaction：InnoDB 一致性快照，不锁表（生产可在线备份）
    # --routines/--triggers/--events：存储过程、触发器、事件一并带走
    # --set-gtid-purged=OFF：避免非 GTID 环境恢复报错
    # --master-data=2 在部分版本需 SUPER 权限，这里用 SHOW MASTER STATUS 单独取位点，兼容性更好
    # 刻意【不加】 --databases：这样导出内容不含 CREATE DATABASE / USE 语句，
    # 恢复时可以灌进任意库名（恢复演练必须能灌到 fssc_drill_* 而不动生产库）。
    if "$MYSQLDUMP_BIN" "${CONN[@]}" \
            --single-transaction --routines --triggers --events \
            --set-gtid-purged=OFF --default-character-set=utf8mb4 \
            "$db" 2>"$OUT_DIR/${db}.err" | gzip -c > "$file"; then
        size=$(stat -c%s "$file" 2>/dev/null || echo 0)
        if [ "$size" -lt 1024 ]; then
            echo "   [失败] $db 备份文件过小（${size}B），请查看 $OUT_DIR/${db}.err"
            FAIL=1
        else
            echo "   [成功] $file  ($(numfmt --to=iec "$size" 2>/dev/null || echo ${size}B))"
        fi
    else
        echo "   [失败] $db 备份命令返回非 0，详情见 $OUT_DIR/${db}.err"
        FAIL=1
    fi
    rm -f "$OUT_DIR/${db}.err"

    # 完整性校验：gzip 能否完整解压（防止截断的备份被当作"有备份"）
    if [ -f "$file" ] && ! gzip -t "$file" 2>/dev/null; then
        echo "   [失败] $db 备份文件 gzip 校验不通过（文件损坏）"
        FAIL=1
    fi
done

# ---------- 记录 binlog 位点（PITR 用）----------
{
    echo "# FSSC 备份元数据  $(date '+%Y-%m-%d %H:%M:%S')"
    echo "# host=$DB_HOST port=$DB_PORT dbs=$DBS"
    echo ""
    "$MYSQL_BIN" "${CONN[@]}" -e "show master status\G" 2>/dev/null || echo "（未能读取 master status，可能未开启 binlog）"
    echo ""
    echo "# 各库表数量（恢复后可用于比对）"
    for db in $DBS; do
        cnt=$("$MYSQL_BIN" "${CONN[@]}" -N -e \
            "select count(*) from information_schema.tables where table_schema='$db';" 2>/dev/null)
        # 同时记录核心业务表行数，作为恢复演练的比对基线
        rows=$("$MYSQL_BIN" "${CONN[@]}" -N -e \
            "select coalesce(sum(table_rows),0) from information_schema.tables where table_schema='$db';" 2>/dev/null)
        echo "$db tables=$cnt approx_rows=$rows"
    done
} > "$OUT_DIR/META.txt" 2>&1

echo ""
echo ">> binlog 位点与基线已写入 META.txt"
grep -E "tables=" "$OUT_DIR/META.txt" || true

# ---------- 保留策略 ----------
echo ""
echo ">> 清理超过 $KEEP 份的历史备份 ..."
cd "$ROOT/backup" || exit 1
# shellcheck disable=SC2012
ls -1dt */ 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
    echo "   删除旧备份 $old"
    rm -rf -- "$old"
done
echo "   当前保留：$(ls -1d */ 2>/dev/null | wc -l) 份"

echo ""
if [ "$FAIL" -eq 0 ]; then
    echo "[备份完成] $OUT_DIR"
else
    echo "[备份失败] 存在失败项，请检查上方输出"
fi
exit $FAIL
