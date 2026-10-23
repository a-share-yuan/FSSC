#!/usr/bin/env bash
# =============================================================================
# FSSC 演示数据库一键导入（bash 版；Windows 用同目录 import.bat）
#
#   导入 db/demo-db/*.sql（323 个分表文件）到本机 MySQL：
#     · fssc        主库：316 张业务/平台表 + Flowable ACT_*（含 315 个已部署流程定义）
#     · fssc_image  影像库：img_* / image_* / ocr_record 共 6 张表
#
#   每个文件自带 CREATE DATABASE IF NOT EXISTS + USE，无需预先建库；
#   每张表都是 DROP TABLE IF EXISTS + CREATE TABLE + INSERT，可重复执行（幂等）。
#   顺序约束：fssc_routines.sql（视图）引用业务表，必须最后导入。
#
# 用法：
#   ./db/import.sh                          # 默认 127.0.0.1:3306 root，口令交互输入
#   ./db/import.sh -h 10.0.0.11 -P 3307 -u fssc
#   MYSQL_PWD='your-pass' ./db/import.sh    # 非交互（CI 用）
#
# 环境变量：FSSC_DB_HOST / FSSC_DB_PORT / FSSC_DB_USER / MYSQL_PWD
# =============================================================================
set -euo pipefail

HOST="${FSSC_DB_HOST:-127.0.0.1}"
PORT="${FSSC_DB_PORT:-3306}"
USER_NAME="${FSSC_DB_USER:-root}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/demo-db"

while getopts "h:P:u:d:" opt; do
  case "$opt" in
    h) HOST="$OPTARG" ;;
    P) PORT="$OPTARG" ;;
    u) USER_NAME="$OPTARG" ;;
    d) DIR="$OPTARG" ;;
    *) echo "usage: $0 [-h host] [-P port] [-u user] [-d dump_dir]" >&2; exit 2 ;;
  esac
done

[ -d "$DIR" ] || { echo "ERROR: dump dir not found: $DIR" >&2; exit 1; }
command -v mysql >/dev/null 2>&1 || { echo "ERROR: mysql client not found in PATH" >&2; exit 1; }

# 口令只走 MYSQL_PWD；未设置则交给 mysql 交互提示（不要写在命令行参数里）
if [ -n "${MYSQL_PWD:-}" ]; then export MYSQL_PWD; fi

# 先全部表文件（两库均可，各自 USE 自己的库），视图/存储过程最后
mapfile -t FILES < <(find "$DIR" -maxdepth 1 -name '*.sql' ! -name 'fssc_routines.sql' | sort)
ROUTINES="$DIR/fssc_routines.sql"
[ -f "$ROUTINES" ] && FILES+=("$ROUTINES")

echo "==> importing ${#FILES[@]} sql files into ${USER_NAME}@${HOST}:${PORT}"
echo "    (tables first, views/routines last)"

cat "${FILES[@]}" | mysql -h "$HOST" -P "$PORT" -u "$USER_NAME" --default-character-set=utf8mb4

echo "==> done. verify with:"
echo "    mysql -h $HOST -u $USER_NAME -e 'SELECT COUNT(*) FROM fssc.sys_user;'   # expect 460"
