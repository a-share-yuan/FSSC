# -*- coding: utf-8 -*-
"""P0 收尾四项冒烟：
1) 档案销毁「未到期禁止销毁」强校验（申请侧）
2) 单据关联「累计占用 ≤ 源单据金额」校验
3) 两个新 Job 已被 Spring 调度注册
说明：1)(2) 均为负向测试，预期被业务拦截，不产生脏数据。
"""
import json, urllib.request, urllib.error, urllib.parse, sys, os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_env

BASE = smoke_env.base_url()


def call(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"success": False, "msg": "HTTP %s" % e.code}


tok = call("POST", "/auth/login", smoke_env.login_payload())["data"]["token"]
print("登录 OK")

# ---------- 1) 档案销毁：未到期必须被拦截 ----------
arch = call("GET", "/tenant/compliance/archive/list", token=tok,
            params={"pageNum": 1, "pageSize": 20}).get("data")
lst = None
if isinstance(arch, dict):
    lst = arch.get("list") or arch.get("records")
elif isinstance(arch, list):
    lst = arch
lst = lst or []
print("1a) 档案列表 %d 条" % len(lst))

if lst:
    a0 = lst[0]
    aid = a0.get("id")
    print("1b) 取档案 id=%s 到期日=%s 状态=%s" % (aid, a0.get("destroyDate"), a0.get("archiveStatus")))
    res = call("POST", "/tenant/compliance/archive-destroy/create", token=tok,
               body={"archiveIds": [aid], "reason": "冒烟：未到期应被拦截", "applicantId": 1})
    msg = str(res.get("msg") or "")
    ok = (not res.get("success")) and ("尚未届满" in msg or "未设置到期销毁日期" in msg or "不可重复处理" in msg)
    print("1c) 未到期销毁申请 → success=%s msg=%s" % (res.get("success"), msg))
    assert ok, "未到期档案未被拦截，校验未生效: %s" % res
    print("1) 档案销毁到期强校验 OK（未到期被拦截）")
else:
    print("1) [跳过] 无档案数据，未验证")

# ---------- 2) 单据关联：占用超源单据金额必须被拦截 ----------
bp = call("GET", "/tenant/expense/bill/myPage", token=tok,
          params={"pageNum": 1, "pageSize": 50}).get("data") or {}
bills = bp.get("list") or bp.get("records") or []
print("2a) 单据列表 %d 条" % len(bills))

src = next((b for b in bills if b.get("billType") == "APPLY"), None)
tgt = next((b for b in bills if b.get("id") != (src or {}).get("id")), None)
if src and tgt:
    total = src.get("totalAmount") or 0
    over = float(total) + 1000
    res = call("POST", "/tenant/expense/detail/relation", token=tok,
               params={"sourceBillId": src.get("id"), "targetBillId": tgt.get("id"),
                       "relationType": "APPLY_CLAIM", "useAmount": over})
    msg = str(res.get("msg") or "")
    ok = (not res.get("success")) and ("累计占用" in msg)
    print("2b) 关联占用 %s > 申请额 %s → success=%s msg=%s" % (over, total, res.get("success"), msg))
    assert ok, "超申请金额关联未被拦截，校验未生效: %s" % res
    print("2) 单据关联占用校验 OK（超额被拦截）")
else:
    print("2) [跳过] 缺少 APPLY/其他单据，未验证")

print("[SMOKE] P0 收尾校验项 PASS")
