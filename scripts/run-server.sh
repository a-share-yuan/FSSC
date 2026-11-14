#!/usr/bin/env bash
# =============================================================
# FSSC 后端启动脚本（Git Bash / Windows）
# 用法:
#   bash scripts/run-server.sh [--port 8089] [--profiles dev] [--force-restart]
# 说明:
#   1. 显式 --server.port 覆盖环境变量 SERVER__PORT=0 的干扰
#      （WorkBuddy/部分环境会注入该变量，Spring relaxed binding
#        会把 server.port 解析成 0 = 随机端口）。
#   2. 锁单一 java 二进制：固定解析本项目已知 JDK，规避 PATH 中
#      javapath / 多 JDK 竞态导致同一 jar 被不同 java 拉起双进程。
#   3. 启动前检测端口是否已被监听：已有实例在跑则拒绝二次启动，
#      避免双进程抢占 8089；--force-restart 时先停旧实例再启动。
#   4. 写 PID 文件（.run/fssc-server-<port>.pid）作单实例兜底守卫。
#   5. JDK 26 运行兜底参数：放开原生访问与模块反射（仅开发机用，
#      生产建议 JDK 21，可去掉 --add-opens）。
#   6. 数据库连接见 src/main/resources/application-dev.yml（默认
#      192.168.20.77:3306/fssc, root/123456）。
# =============================================================
set -e
cd "$(dirname "$0")/.."

PORT=8089
PROFILES=dev
JAR=fssc-server/fssc-start/target/fssc-server.jar
FORCE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --port) PORT="$2"; shift 2;;
    --profiles) PROFILES="$2"; shift 2;;
    --force-restart) FORCE=1; shift;;
    *) echo "未知参数: $1"; exit 1;;
  esac
done

# ---------- 1. 锁定单一 java 二进制（杜绝 PATH javapath/多 JDK 竞态） ----------
JAVA_BIN=""
for cand in "/d/JDK/bin/java" "$JAVA_HOME/bin/java"; do
  if [ -n "$cand" ] && [ -x "$cand" ]; then JAVA_BIN="$cand"; break; fi
done
if [ -z "$JAVA_BIN" ] && command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"   # 仅兜底，通常应命中上面的固定 JDK
fi
if [ -z "$JAVA_BIN" ]; then
  echo "[错误] 未找到 java，请设置 JAVA_HOME 或在 PATH 加入 JDK"; exit 1
fi
JAVA_VER="$("$JAVA_BIN" -version 2>&1 | sed -n '1p')"

# ---------- 2. 端口占用检测（防双进程抢占 8089） ----------
listener_line=$(netstat -ano 2>/dev/null | grep -E "LISTENING" | grep -E ":${PORT}[[:space:]]" || true)
if [ -n "$listener_line" ]; then
  occupier_pid=$(echo "$listener_line" | awk '{print $NF}' | head -1)
  if [ "$FORCE" = "1" ]; then
    echo ">> 端口 ${PORT} 被 PID=${occupier_pid} 占用，--force-restart 将停止旧实例"
    taskkill //PID "$occupier_pid" //F >/dev/null 2>&1 || kill -9 "$occupier_pid" 2>/dev/null || true
    sleep 2
  else
    echo "[错误] 端口 ${PORT} 已被进程 PID=${occupier_pid} 监听，已有一个实例在运行。"
    echo "       如需重启，请先停止旧实例，或使用: bash scripts/run-server.sh --port ${PORT} --force-restart"
    exit 2
  fi
fi

# ---------- 3. PID 文件单实例守卫（同端口防重复拉起） ----------
PID_FILE=".run/fssc-server-${PORT}.pid"
mkdir -p .run
if [ -f "$PID_FILE" ]; then
  old_pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
  if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
    if [ "$FORCE" = "1" ]; then
      echo ">> PID 文件命中旧实例 ${old_pid} 仍在运行，--force-restart 将停止旧实例"
      taskkill //PID "$old_pid" //F >/dev/null 2>&1 || kill -9 "$old_pid" 2>/dev/null || true
      sleep 2
    else
      echo "[错误] 检测到旧实例 PID=${old_pid} 仍在运行（PID 文件: $PID_FILE），已放弃启动。"
      echo "       如需重启: bash scripts/run-server.sh --port ${PORT} --force-restart"
      exit 3
    fi
  fi
fi

# ---------- 4. 校验 jar 与参数 ----------
if [ ! -f "$JAR" ]; then
  echo "未找到 $JAR，先执行: python scripts/mvn.py -q package -DskipTests"
  exit 1
fi

# ---------- 5. 记录 PID 并以 rw 运行（exec 保持为 java 进程 PID） ----------
echo "$$" > "$PID_FILE"
echo ">> 启动 FSSC: port=$PORT profiles=$PROFILES pid=$$ java=$JAVA_BIN ($JAVA_VER)"
exec "$JAVA_BIN" \
  --enable-native-access=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  -jar "$JAR" \
  --server.port="$PORT" \
  --spring.profiles.active="$PROFILES"