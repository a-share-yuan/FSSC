#!/usr/bin/env bash
# =============================================================================
# FSSC 发布版本管理：快照 / 列表 / 回滚（P0-B2 变更安全）
#
#   解决的问题：原部署方式是「杀进程 → 换 jar → 重启」，一旦新版有问题，
#   旧 jar 已被覆盖，**无法回退**。本脚本把每次可发布的 jar 存档为带版本号的快照，
#   回滚 = 用上一份快照覆盖 target jar 并重启，分钟级完成。
#
# 用法：
#   bash scripts/release.sh snapshot                  # 当前构建产物存档（版本取 pom 1.0.0）
#   bash scripts/release.sh snapshot --version 1.0.1  # 指定版本号
#   bash scripts/release.sh list                      # 查看所有快照（最新在前）
#   bash scripts/release.sh rollback                  # 回滚到上一份快照并重启（默认端口 8089）
#   bash scripts/release.sh rollback --to <快照文件名> # 回滚到指定快照
#   bash scripts/release.sh current                   # 显示当前生效快照
#
# 推荐流程：构建 → 启动验证(冒烟+E2E) → 通过后 snapshot → 记录到发布记录
# =============================================================================
set -u -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/fssc-server/fssc-start/target/fssc-server.jar"
REL_DIR="$ROOT/releases"
KEEP=8          # 保留最近 N 份快照
PORT=8089

mkdir -p "$REL_DIR"

CMD="${1:-}"
shift || true

VERSION="1.0.0"
TO=""
while [ $# -gt 0 ]; do
    case "$1" in
        --version) VERSION="$2"; shift 2 ;;
        --to)      TO="$2"; shift 2 ;;
        --port)    PORT="$2"; shift 2 ;;
        *) echo "[错误] 未知参数：$1"; exit 2 ;;
    esac
done

# ---------- 从 pom 读版本（未显式指定时）----------
read_pom_version() {
    local pom="$ROOT/fssc-server/pom.xml"
    [ -f "$pom" ] || return
    local v
    v=$(grep -m1 -oP '(?<=<version>)[^<]+' "$pom" 2>/dev/null || true)
    [ -n "$v" ] && VERSION="$v"
}

current_snapshot() {
    [ -f "$REL_DIR/.current" ] && cat "$REL_DIR/.current" || echo "(未记录)"
}

do_snapshot() {
    [ -z "$VERSION" ] && read_pom_version
    if [ ! -f "$JAR" ]; then
        echo "[错误] 构建产物不存在：$JAR"
        echo "       请先执行：cd fssc-server && python ../scripts/mvn.py clean package"
        exit 1
    fi
    local ts name
    ts="$(date +%Y%m%d-%H%M%S)"
    name="fssc-server-${VERSION}-${ts}.jar"
    cp -f "$JAR" "$REL_DIR/$name"
    echo "$name" > "$REL_DIR/.current"
    {
        echo "$(date '+%Y-%m-%d %H:%M:%S')  snapshot  version=$VERSION  file=$name  size=$(stat -c%s "$JAR" 2>/dev/null)B"
    } >> "$REL_DIR/history.log"
    echo "[快照完成] $REL_DIR/$name"
    echo "           当前生效：$name"

    echo ">> 清理超过 $KEEP 份的历史快照 ..."
    # shellcheck disable=SC2012
    ls -1t "$REL_DIR"/fssc-server-*.jar 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
        echo "   删除 $old"
        rm -f -- "$old"
    done
}

do_list() {
    echo "快照目录：$REL_DIR"
    echo "当前生效：$(current_snapshot)"
    echo "-------------------------------------------------------------------"
    if ls -1t "$REL_DIR"/fssc-server-*.jar >/dev/null 2>&1; then
        local cur; cur="$(current_snapshot)"
        # shellcheck disable=SC2012
        ls -1t "$REL_DIR"/fssc-server-*.jar | while read -r f; do
            local base size mark
            base="$(basename "$f")"
            size="$(stat -c%s "$f" 2>/dev/null || echo 0)"
            [ "$base" = "$cur" ] && mark="  <== 当前" || mark=""
            printf "  %-46s %10s B%s\n" "$base" "$size" "$mark"
        done
    else
        echo "  （暂无快照，先执行 release.sh snapshot）"
    fi
    echo "-------------------------------------------------------------------"
    [ -f "$REL_DIR/history.log" ] && tail -10 "$REL_DIR/history.log"
}

do_rollback() {
    local target="$TO"
    if [ -z "$target" ]; then
        # 取"最近一份但不是当前生效"的快照 = 上一版本
        local cur; cur="$(current_snapshot)"
        target=""
        # shellcheck disable=SC2012
        for f in $(ls -1t "$REL_DIR"/fssc-server-*.jar 2>/dev/null); do
            if [ "$(basename "$f")" != "$cur" ]; then
                target="$f"; break
            fi
        done
        if [ -z "$target" ]; then
            echo "[错误] 找不到可回滚的上一版本快照（只有一份或没有快照）"
            echo "       现有："; do_list
            exit 1
        fi
    else
        case "$target" in
            /*) : ;;
            *) target="$REL_DIR/$target" ;;
        esac
    fi

    if [ ! -f "$target" ]; then
        echo "[错误] 目标快照不存在：$target"
        exit 1
    fi

    echo "=============================================="
    echo " FSSC 版本回滚"
    echo " 目标快照 : $(basename "$target")"
    echo " 回滚前   : $(current_snapshot)"
    echo " 端口     : $PORT"
    echo "=============================================="
    read -r -p "确认回滚？输入 yes 继续：" ans
    [ "$ans" = "yes" ] || { echo "已取消。"; exit 1; }

    # 1) 停旧实例
    local pid
    pid="$(netstat -ano 2>/dev/null | grep LISTENING | grep -E ":${PORT}[[:space:]]" | awk '{print $NF}' | head -1)"
    if [ -n "${pid:-}" ]; then
        echo ">> 停止旧实例 PID=$pid"
        taskkill //F //PID "$pid" >/dev/null 2>&1 || powershell -NoProfile -Command "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
        sleep 3
    fi

    # 2) 覆盖 target jar
    echo ">> 覆盖 target jar"
    cp -f "$target" "$JAR"
    echo "$(basename "$target")" > "$REL_DIR/.current"
    echo "$(date '+%Y-%m-%d %H:%M:%S')  rollback  to=$(basename "$target")" >> "$REL_DIR/history.log"

    # 3) 重启
    echo ">> 重启服务（端口 $PORT，脚本内置 120 秒 LISTENING 等待）"
    cd "$ROOT" && bash scripts/run-server.sh --port "$PORT"

    echo ""
    echo "[回滚完成] 当前生效：$(basename "$target")"
    echo "验证：curl http://127.0.0.1:$PORT/api/health"
}

case "$CMD" in
    snapshot) do_snapshot ;;
    list)     do_list ;;
    current)  echo "$(current_snapshot)" ;;
    rollback) do_rollback ;;
    *)
        sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
        exit 2
        ;;
esac
