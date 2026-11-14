# -*- coding: utf-8 -*-
"""共享运营 8.2 冒烟：派单规则 CRUD / 任务池管理（撤回/重分配/退单）/ 凭证分录修改"""
import json, urllib.request, urllib.error, urllib.parse

BASE = "http://127.0.0.1:18080/api"

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
        return json.loads(e.read().decode())

tok = call("POST", "/auth/login", {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"})["data"]["token"]
print("0) 登录 OK")

# 1) 菜单
mt = call("GET", "/tenant/system/menu/tree", token=tok).get("data") or []
def flat(n, o):
    o.append(n); [flat(c, o) for c in (n.get("children") or [])]
allm = []; [flat(m, allm) for m in mt]
names = {m.get("menuName") for m in allm}
for e in ["抢派单规则", "任务池管理", "工单处理", "岗位设置"]:
    assert e in names, f"菜单缺失 {e}"
print("1) 菜单 OK: 抢派单规则/任务池/工单处理/岗位设置")

# 2) 派单规则 CRUD
rp = call("GET", "/tenant/sharing/config/dispatch-rule/page", token=tok, params={"pageNum": 1, "pageSize": 10}).get("data") or {}
print("2) 派单规则列表:", len(rp.get("list") or []), "条, 首条=", ((rp.get("list") or [{}])[0].get("ruleName")))
assert (rp.get("total") or 0) >= 3
rid = call("POST", "/tenant/sharing/config/dispatch-rule/create", token=tok, body={
    "ruleCode": "DR-TEST", "ruleName": "测试规则", "taskType": "AUDIT",
    "targetGroupId": 1, "targetGroupName": "费用组", "mode": "MANUAL", "priority": 9, "status": "NORMAL"})["data"]
assert rid
ru = call("POST", "/tenant/sharing/config/dispatch-rule/update", token=tok, body={"id": rid, "priority": 2, "mode": "SYSTEM"})
assert ru.get("success"), ru
rd = call("POST", f"/tenant/sharing/config/dispatch-rule/{rid}/delete", token=tok)
assert rd.get("success"), rd
print("3) 派单规则 新增->更新->删除 OK")

# 4) 任务池管理：找一个 WAITING 任务做 认领->撤回->重分配->退单->完成 全链路
mgr = call("GET", "/tenant/sharing/task/manage", token=tok, params={"pageNum": 1, "pageSize": 5}).get("data") or {}
tasks = mgr.get("list") or []
print("4) 任务池全量:", mgr.get("total"), "条, 状态分布:", {t.get("poolStatus") for t in tasks})
pool = call("GET", "/tenant/sharing/task/pool", token=tok, params={"pageNum": 1, "pageSize": 5}).get("data") or {}
ptasks = pool.get("list") or []
assert ptasks, "任务池无待认领任务"
tid = ptasks[0]["id"]
r = call("POST", f"/tenant/sharing/task/{tid}/receive", token=tok)
assert r.get("success"), r
print("5) 认领 OK id=", tid)
w = call("POST", f"/tenant/sharing/task/{tid}/withdraw", token=tok)
assert w.get("success"), w
print("6) 撤回 OK")
r2 = call("POST", f"/tenant/sharing/task/{tid}/receive", token=tok)
assert r2.get("success"), r2
ra = call("POST", f"/tenant/sharing/task/{tid}/reassign", token=tok, params={"assigneeId": 1})
assert ra.get("success"), ra
print("7) 重新分配 OK")
rt = call("POST", f"/tenant/sharing/task/{tid}/return", token=tok, params={"reason": "单据附件不清晰，退回补充"})
assert rt.get("success"), rt
print("8) 退单 OK")
r3 = call("POST", f"/tenant/sharing/task/{tid}/receive", token=tok)
assert r3.get("success"), r3
f = call("POST", f"/tenant/sharing/task/{tid}/finish", token=tok)
assert f.get("success"), f
print("9) 完成 OK")

# 5) 凭证分录修改：找一个凭证改一行再还原
vp = call("GET", "/tenant/sharing/voucher/page", token=tok, params={"pageNum": 1, "pageSize": 5}).get("data") or {}
vs = vp.get("list") or []
if vs:
    vid = vs[0]["id"]
    lines = (call("GET", f"/tenant/sharing/voucher/{vid}/lines", token=tok).get("data") or [])
    assert lines
    old = [dict(l) for l in lines]
    lines[0]["summary"] = (lines[0].get("summary") or "") + "T"
    u = call("POST", f"/tenant/sharing/voucher/{vid}/lines/update", token=tok, body=lines)
    assert u.get("success"), u
    call("POST", f"/tenant/sharing/voucher/{vid}/lines/update", token=tok, body=old)
    print("10) 凭证分录修改->还原 OK (凭证", vs[0].get("voucherNo"), ")")
else:
    print("10) 无凭证可测（跳过分录修改）")

print("[SMOKE] 共享运营 8.2 PASS")
