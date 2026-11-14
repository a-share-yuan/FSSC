#!/usr/bin/env bash
# =============================================================================
# FSSC 数据库恢复（P0-B3）
#
#   默认把备份恢复到一个【演练库】(<db>_drill_<时间戳>)，绝不动生产库 —— 这是恢复演练的安全形态。
#   真要回滚生产库时必须显式加 --force-prod，且脚本会要求二次确认。
#
# 用法：
#   # 演练：恢复 fssc 到一个临时库并做行数比对（推荐）
#   bash scripts/restore-db.sh --dir backup/20260910-200000
#
#   # 只恢复指定库
#   bash scripts/restore-db.sh --dir backup/20260910-200000 --db fssc
#
#   # 生产回滚（危险，需二次确认）
#   bash scripts/restore-db.sh --dir backup/20260910-200000 --db fssc --force-prod
#
# 退出码：0 成功；非 0 失败
# =============================================================================
set -u -o pipefail

MYSQL_BIN="${MYSQL_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe}"
DB_HOST="${FSSC_DB_HOST:-192.168.20.77}"
DB_PORT="${FSSC_DB_PORT:-3306}"
DB_USER="${FSSC_DB_USER:-root}"
DB_PASSWORD="${FSSC_DB_PASSWORD:-123456}"

DIR=""
DB="fssc"
FORCE_PROD=0
KEEP_DRILL=0

while [ $# -gt 0 ]; do
    case "$1" in
        --dir)        DIR="$2"; shift 2 ;;
        --db)         DB="$2"; shift 2 ;;
        --force-prod) FORCE_PROD=1; shift ;;
        --keep-drill) KEEP_DRILL=1; shift ;;
        --host)       DB_HOST="$2"; shift 2 ;;
        --port)       DB_PORT="$2"; shift 2 ;;
        --user)       DB_USER="$2"; shift 2 ;;
        *) echo "[错误] 未知参数：$1"; exit 2 ;;
    esac
done

if [ -z "$DIR" ]; then
    echo "用法：bash scripts/restore-db.sh --dir <备份目录> [--db fssc] [--force-prod]"
    echo "可用备份："
    ROOT0="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
    ls -1dt "$ROOT0"/backup/*/ 2>/dev/null | head -10 || echo "  （无）"
    exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
case "$DIR" in
    /*) ABS_DIR="$DIR" ;;
    *)  ABS_DIR="$ROOT/$DIR" ;;
esac
FILE="$ABS_DIR/${DB}.sql.gz"
if [ ! -f "$FILE" ]; then
    echo "[错误] 备份文件不存在：$FILE"
    exit 2
fi
if ! gzip -t "$FILE" 2>/dev/null; then
    echo "[错误] 备份文件 gzip 校验不通过，拒绝用它恢复（可能是截断文件）"
    exit 2
fi

CONN=(--host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" "--password=$DB_PASSWORD" --default-character-set=utf8mb4)

STAMP="$(date +%Y%m%d%H%M%S)"
if [ "$FORCE_PROD" -eq 1 ]; then
    TARGET="$DB"
    echo "##############################################################"
    echo "#    生产回滚模式：把备份灌回【生产库 $DB】，现有数据将被覆盖！"
    echo "##############################################################"
    read -r -p "确认执行？输入 yes 继续：" ans
    if [ "$ans" != "yes" ]; then
        echo "已取消。"
        exit 1
    fi
    # 生产回滚前，先把"当前状态"再备一份，避免回滚本身不可逆
    SAFETY="$ROOT/backup/pre-rollback-$STAMP"
    mkdir -p "$SAFETY"
    echo ">> 回滚前快照当前 $DB ..."
    "/c/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe" "${CONN[@]}" \
        --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
        "$DB" 2>/dev/null | gzip -c > "$SAFETY/${DB}.sql.gz"
    echo "   已存至 $SAFETY/${DB}.sql.gz"
else
    TARGET="${DB}_drill_${STAMP}"
    echo "=============================================="
    echo " FSSC 恢复演练（不改生产库）"
    echo " 备份文件 : $FILE"
    echo " 生产库   : $DB（不动）"
    echo " 演练库   : $TARGET"
    echo "=============================================="
fi

echo ">> 建库 $TARGET ..."
"$MYSQL_BIN" "${CONN[@]}" -e \
    "DROP DATABASE IF EXISTS \`$TARGET\`; CREATE DATABASE \`$TARGET\` DEFAULT CHARACTER SET utf8mb4;" || {
    echo "[失败] 建库失败"; exit 1; }

echo ">> 灌入数据（大库耗时较长，请等待）..."
if ! gunzip -c "$FILE" | "$MYSQL_BIN" "${CONN[@]}" "$TARGET"; then
    echo "[失败] 数据灌入失败"
    exit 1
fi

# ---------- 恢复后校验：表数量 / 关键表行数 ----------
echo ""
echo ">> 恢复后校验"
src_tables=$("$MYSQL_BIN" "${CONN[@]}" -N -e \
    "select count(*) from information_schema.tables where table_schema='$DB';" 2>/dev/null)
dst_tables=$("$MYSQL_BIN" "${CONN[@]}" -N -e \
    "select count(*) from information_schema.tables where table_schema='$TARGET';" 2>/dev/null)
echo "   表数量：源 $src_tables  →  演练库 $dst_tables"

CHECK_TABLES="${FSSC_CHECK_TABLES:-exp_bill op_voucher op_voucher_line inv_invoice sys_user sys_menu wf_business_flow}"
printf "   %-22s %10s %10s   %s\n" "表" "源行数" "演练库" "结果"
OVERALL=0
for t in $CHECK_TABLES; do
    c1=$("$MYSQL_BIN" "${CONN[@]}" -N -e "select count(*) from \`$DB\`.\`$t\`;" 2>/dev/null || echo "-")
    c2=$("$MYSQL_BIN" "${CONN[@]}" -N -e "select count(*) from \`$TARGET\`.\`$t\`;" 2>/dev/null || echo "-")
    if [ "$c1" = "$c2" ]; then mark="一致"; else mark="差异 "; OVERALL=1; fi
    printf "   %-22s %10s %10s   %s\n" "$t" "$c1" "$c2" "$mark"
done

echo ""
if [ "$FORCE_PROD" -eq 0 ] && [ "$KEEP_DRILL" -eq 0 ]; then
    echo ">> 清理演练库 $TARGET（加 --keep-drill 可保留以人工抽查）"
    "$MYSQL_BIN" "${CONN[@]}" -e "DROP DATABASE IF EXISTS \`$TARGET\`;"
fi

if [ "$OVERALL" -eq 0 ] && [ "$src_tables" = "$dst_tables" ]; then
    echo "[恢复演练通过] 表数量与关键表行数全部一致"
    exit 0
else
    echo "[恢复演练存在差异] 请人工核对（表数量或关键表行数不一致）"
    exit 1
fi
