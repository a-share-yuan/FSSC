#!/usr/bin/env bash
# =============================================================
# FSSC 数据库初始化脚本
#
# 用法:
#   ./scripts/db-init.sh            # 用默认连接初始化
#   ./scripts/db-init.sh -h 192.168.20.77 -P 3306 -u root -p 123456
#
# 顺序加载 db/ 下所有建表脚本:
#   00-schema.sql       建库 USE fssc
#   01-platform.sql     平台运营域（无 tenant_id，白名单）
#   02-system.sql       租户系统域
#   03-masterdata.sql   主数据域
#   04-budget.sql       预算域
#   05-workflow.sql     流程扩展域
#   06-expense.sql      费用单据域
#   07-invoice-image.sql 影像发票域
#   08-sharing.sql      共享运营域
#
# 注意：Flowable 7 的 ACT_* / FLW_* 表由引擎首次启动时自动创建，
#       本脚本不创建、也不覆盖它们。
# =============================================================
set -euo pipefail

# ---- 默认连接参数 ----
DB_HOST="${DB_HOST:-192.168.20.77}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="fssc"

# ---- 解析命令行覆盖 ----
while getopts "h:P:u:p:" opt; do
  case $opt in
    h) DB_HOST="$OPTARG" ;;
    P) DB_PORT="$OPTARG" ;;
    u) DB_USER="$OPTARG" ;;
    p) DB_PASS="$OPTARG" ;;
    *) echo "用法: $0 [-h host] [-P port] [-u user] [-p password]" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="$SCRIPT_DIR/../db"

echo "==> FSSC 数据库初始化"
echo "    主机: $DB_HOST:$DB_PORT  用户: $DB_USER  库: $DB_NAME"

# 定位 mysql 客户端（Windows 下通常不在 PATH 里）
MYSQL_BIN="${MYSQL_BIN:-}"
if [ -z "$MYSQL_BIN" ]; then
  if command -v mysql >/dev/null 2>&1; then
    MYSQL_BIN="mysql"
  elif [ -x "/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" ]; then
    MYSQL_BIN="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
  else
    echo " 找不到 mysql 客户端，请设置环境变量 MYSQL_BIN 指向 mysql(.exe)。" >&2
    exit 1
  fi
fi

# 构造 mysql 客户端命令
# 【关键】--default-character-set=utf8mb4 不能省！
# Windows 版 mysql 客户端默认 GBK，会把 UTF-8 的中文注释按 GBK 解释，
# 建表能"成功"但所有 COMMENT 全变乱码，且事后无法从库里恢复。
MYSQL_CMD=("$MYSQL_BIN" -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4)
if ! "${MYSQL_CMD[@]}" -e "SELECT 1;" >/dev/null 2>&1; then
  echo " 无法连接 MySQL，请检查连接参数或确保 MySQL 8 已启动。" >&2
  exit 1
fi

# 按文件名顺序执行
shopt -s nullglob
FILES=("$DB_DIR"/*.sql)
IFS=$'\n' FILES=($(printf '%s\n' "${FILES[@]}" | sort))
shopt -u nullglob

for f in "${FILES[@]}"; do
  echo "  -> 执行 $(basename "$f")"
  if ! "${MYSQL_CMD[@]}" < "$f" 2>err.tmp; then
    echo " 执行 $(basename "$f") 失败：" >&2
    cat err.tmp >&2
    rm -f err.tmp
    exit 1
  fi
done
rm -f err.tmp

echo "==> 建表完成。运行合规自检..."
python3 "$SCRIPT_DIR/db-check.py" "$DB_DIR"
