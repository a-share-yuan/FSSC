#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
#
# 导入 db/Dump<日期>/ 下的整库 dump（一表一文件，可重复执行）。
#
# 用法：
#   bash db/import.sh                                  连 127.0.0.1:3306，账号 root
#   bash db/import.sh -h 10.0.0.5 -P 3307 -u root      指定实例
#   MYSQL_PWD=*** bash db/import.sh                    非交互（CI 用这个）
#
# 说明：
#   · 每个 .sql 自带 CREATE DATABASE IF NOT EXISTS + USE + SET FOREIGN_KEY_CHECKS=0
#     与 DROP TABLE IF EXISTS + CREATE + INSERT，所以**不需要预先建库**，文件顺序
#     也不影响成败，整个脚本可以反复执行。
#   · 目录不写死日期：默认取 db/Dump*/ 里字典序最大的那一份（即最新导出）。
#   · 主库 fssc 与影像库 fssc_image 会在导入过程中自动建好。

set -u

HOST="127.0.0.1"
PORT="3306"
DBUSER="root"
DUMPDIR=""

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

while getopts ":h:P:u:d:" opt; do
  case "$opt" in
    h) HOST="$OPTARG" ;;
    P) PORT="$OPTARG" ;;
    u) DBUSER="$OPTARG" ;;
    d) DUMPDIR="$OPTARG" ;;
    *) usage; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "$DUMPDIR" ]; then
  DUMPDIR="$(ls -d "$ROOT"/db/Dump*/ 2>/dev/null | sort | tail -1)"
  DUMPDIR="${DUMPDIR%/}"
fi
if [ -z "$DUMPDIR" ] || [ ! -d "$DUMPDIR" ]; then
  echo "找不到 dump 目录（期望 db/Dump<日期>/）。请确认仓库完整检出。" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "找不到 mysql 客户端。请先安装 MySQL 8 客户端并加入 PATH。" >&2
  exit 1
fi

FILES="$(ls "$DUMPDIR"/*.sql 2>/dev/null | sort)"
TOTAL="$(printf '%s\n' "$FILES" | grep -c .)"
if [ "$TOTAL" -eq 0 ]; then
  echo "目录内没有 .sql 文件：$DUMPDIR" >&2
  exit 1
fi

echo "dump 目录 : $DUMPDIR"
echo "目标实例 : $DBUSER@$HOST:$PORT"
echo "文件数量 : $TOTAL"
echo

if [ -z "${MYSQL_PWD:-}" ]; then
  printf 'MySQL 口令（直接回车表示无口令）: ' >&2
  stty -echo 2>/dev/null || true
  read -r MYSQL_PWD || MYSQL_PWD=""
  stty echo 2>/dev/null || true
  echo >&2
  export MYSQL_PWD
fi

if ! mysql --default-character-set=utf8mb4 -h "$HOST" -P "$PORT" -u "$DBUSER" -e "SELECT 1" >/dev/null 2>&1; then
  echo "连不上 MySQL 或账号口令不对：$DBUSER@$HOST:$PORT" >&2
  echo "排查：MySQL 是否已启动、端口是否正确、MYSQL_PWD 是否符合预期。" >&2
  exit 1
fi

N=0
for f in $FILES; do
  N=$((N + 1))
  printf '\r[%d/%d] %s' "$N" "$TOTAL" "$(basename "$f")"
  if ! mysql --default-character-set=utf8mb4 -h "$HOST" -P "$PORT" -u "$DBUSER" < "$f"; then
    echo >&2
    echo "导入失败，停在：$f" >&2
    echo "排查：该文件是否被截断、MySQL 版本是否 < 8.0、字符集是否 utf8mb4。" >&2
    exit 1
  fi
done
echo

mysql --default-character-set=utf8mb4 -h "$HOST" -P "$PORT" -u "$DBUSER" -e "
SELECT table_schema AS db, COUNT(*) AS tables
FROM information_schema.tables
WHERE table_schema IN ('fssc','fssc_image')
GROUP BY table_schema;" 2>/dev/null || true

echo
echo "导入完成：$TOTAL 个文件。后续步骤见 readme.txt 第 2、3 步（配 .env、起服务）。"
