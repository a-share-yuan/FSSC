#!/usr/bin/env bash
# =====================================================================
# 维度8 STAB-03：生产级 JVM 启动脚本（4G 堆 + G1GC + OOM HeapDump + GC 日志）
# 用法：bash scripts/start-server.sh [jar路径]
# =====================================================================
set -euo pipefail
JAR="${1:-fssc-server/fssc-start/target/fssc-start-1.0.0.jar}"
LOG_DIR="${LOG_DIR:-./logs}"
HEAPDUMP_DIR="$LOG_DIR/heapdump"
mkdir -p "$HEAPDUMP_DIR"

exec java \
  -Xms4g -Xmx4g \
  -XX:MaxMetaspaceSize=512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=16m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath="$HEAPDUMP_DIR/" \
  -XX:+ExitOnOutOfMemoryError \
  -Xlog:gc*:file="$LOG_DIR/gc.log":time,uptime,level,tags:filecount=10,filesize=100M \
  -Dfile.encoding=UTF-8 \
  -jar "$JAR"
