#!/usr/bin/env bash
# =============================================================
# FSSC P2 冒烟验证脚本（真实登录 + 四道防线）
# 用法: bash scripts/verify-demo.sh [port]   (默认 18080)
# 前置: 应用已启动; 种子数据已灌入 (scripts/seed-p2.sql)
# 账号: 平台 admin/Admin@123 ; 租户1 DEMO1/alice/123456 ; 租户2 DEMO2/carol/123456
# 退出码: 0 全部通过；非 0 存在失败
# =============================================================
set -u
PORT="${1:-18080}"
BASE="http://127.0.0.1:${PORT}"
PY="C:/Users/吴碧峰/.workbuddy/binaries/python/versions/3.13.12/python.exe"

FAIL=0
check() { # $1=用例名 $2=实际 $3=期望子串
  if echo "$2" | grep -q "$3"; then
    echo "   $1"
  else
    echo "   $1 -> 期望包含[$3], 实际: $(echo "$2" | head -c 120)"
    FAIL=1
  fi
}

login() { # $1=json
  curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" -d "$1" \
    | $PY -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null
}

echo "== 1. 健康检查 =="
if ! curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" --max-time 5 2>/dev/null | grep -q "200\|400\|405"; then
  echo "   应用未就绪 (${BASE})，请先启动: bash scripts/run-server.sh --port ${PORT}"
  exit 1
fi
echo "   应用可达"

echo "== 2. 真实登录（BCrypt + 租户生命周期）=="
T1=$(login '{"username":"alice","password":"123456","subjectType":"TENANT","tenantCode":"DEMO1"}')
T2=$(login '{"username":"carol","password":"123456","subjectType":"TENANT","tenantCode":"DEMO2"}')
TP=$(login '{"username":"admin","password":"Admin@123","subjectType":"PLATFORM"}')
[ -n "$T1" ] && [ -n "$T2" ] && [ -n "$TP" ] && echo "   三主体真实登录成功" || { echo "   登录失败"; exit 1; }
check "错误密码被拒" "$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"alice","password":"wrong","subjectType":"TENANT","tenantCode":"DEMO1"}')" '"code":401'
check "未知企业被拒" "$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"alice","password":"123456","subjectType":"TENANT","tenantCode":"NOPE"}')" '"code":401'

echo "== 3. ① 身份防线 =="
check "租户上下文(含功能码)" "$(curl -s "$BASE/api/tenant/demo/me" -H "Authorization: Bearer $T1")" '"tenantId":1'
check "套餐功能码已装载" "$(curl -s "$BASE/api/tenant/demo/me" -H "Authorization: Bearer $T1")" 'F_EXPENSE_CLAIM'

echo "== 4. ④ 数据防线（双租户隔离）=="
R1=$(curl -s "$BASE/api/tenant/demo/users" -H "Authorization: Bearer $T1")
R2=$(curl -s "$BASE/api/tenant/demo/users" -H "Authorization: Bearer $T2")
U1=$(echo "$R1" | $PY -c "import sys,json; print(','.join(sorted(u['userName'] for u in json.load(sys.stdin)['data'])))" 2>/dev/null)
U2=$(echo "$R2" | $PY -c "import sys,json; print(','.join(sorted(u['userName'] for u in json.load(sys.stdin)['data'])))" 2>/dev/null)
check "租户1 只见自己人(alice,bob)" "$U1" "alice,bob"
check "租户2 只见自己人(carol)" "$U2" "carol"
C1=$(curl -s "$BASE/api/tenant/demo/userCount" -H "Authorization: Bearer $T1" | $PY -c "import sys,json; print(json.load(sys.stdin)['data'])" 2>/dev/null)
C2=$(curl -s "$BASE/api/tenant/demo/userCount" -H "Authorization: Bearer $T2" | $PY -c "import sys,json; print(json.load(sys.stdin)['data'])" 2>/dev/null)
# 计数断言：租户1 至少 2 人（种子 alice/bob，P3 后可能新增 dave 等）；租户2 恰 1 人
if [ "${C1:-0}" -ge 2 ] 2>/dev/null; then echo "   租户1 计数>=2 (实际=$C1)"; else echo "   租户1 计数>=2 (实际=$C1)"; FAIL=1; fi
check "租户2 计数=1" "$C2" "^1$"

echo "== 5. ③ 权限防线（RBAC：角色→菜单→权限码）=="
check "alice有demo:secret放行" "$(curl -s "$BASE/api/tenant/demo/secret" -H "Authorization: Bearer $T1")" '"code":0'
check "carol无权限 4030" "$(curl -s "$BASE/api/tenant/demo/secret" -H "Authorization: Bearer $T2")" '"code":4030'

echo "== 6. 两级路由 =="
check "租户访问平台 4032" "$(curl -s "$BASE/api/platform/demo/info" -H "Authorization: Bearer $T1")" '"code":4032'
check "平台访问租户 4033" "$(curl -s "$BASE/api/tenant/demo/me" -H "Authorization: Bearer $TP")" '"code":4033'
check "平台路由放行" "$(curl -s "$BASE/api/platform/demo/info" -H "Authorization: Bearer $TP")" '"code":0'

echo "== 7. 平台运营接口 =="
check "租户分页(超管通配权限)" "$(curl -s "$BASE/api/platform/tenant/page" -H "Authorization: Bearer $TP")" '"code":0'
check "套餐列表" "$(curl -s "$BASE/api/platform/package/list" -H "Authorization: Bearer $TP")" '"code":0'
check "功能列表" "$(curl -s "$BASE/api/platform/feature/list" -H "Authorization: Bearer $TP")" '"code":0'
check "租户Token访问平台接口 4032(路由先行)" "$(curl -s "$BASE/api/platform/tenant/page" -H "Authorization: Bearer $T1")" '"code":4032'

echo "== 8. 未认证 =="
check "无Token 401" "$(curl -s "$BASE/api/tenant/demo/me")" '"code":401'

echo ""
if [ "$FAIL" = "0" ]; then
  echo " 全部冒烟用例通过"
  exit 0
else
  echo " 存在失败用例"
  exit 1
fi
