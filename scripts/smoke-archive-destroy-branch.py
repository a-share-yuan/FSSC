# -*- coding: utf-8 -*-
"""档案销毁到期校验三分支验证（造数据→验证→清理）。
分支 A: destroy_date 未来  → 必须拦截「尚未届满」
分支 B: destroy_date 过去  → 允许创建销毁申请（合规放行）
分支 C: 已销毁档案         → 必须拦截「不可重复处理」
"""
import json, urllib.request, urllib.error, urllib.parse, sys, os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_env

BASE = smoke_env.base_url()
AID = 9


def sql(s):
    smoke_env.run_sql(s)


def call(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
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

created_ids = []
try:
    # 分支 A：未来到期日 → 拦截
    sql("UPDATE op_archive SET destroy_date='2030-01-01' WHERE id=%d;" % AID)
    r = call("POST", "/tenant/compliance/archive-destroy/create", token=tok,
             body={"archiveIds": [AID], "reason": "分支A-未到期", "applicantId": 1})
    msg = str(r.get("msg") or "")
    print("A) 到期日 2030-01-01 → success=%s msg=%s" % (r.get("success"), msg))
    assert (not r.get("success")) and "尚未届满" in msg, "分支A 未拦截: %s" % r
    print("A) 未到期拦截 OK")

    # 分支 B：过去到期日 → 放行
    sql("UPDATE op_archive SET destroy_date='2020-01-01' WHERE id=%d;" % AID)
    r = call("POST", "/tenant/compliance/archive-destroy/create", token=tok,
             body={"archiveIds": [AID], "reason": "分支B-已到期", "applicantId": 1})
    print("B) 到期日 2020-01-01 → success=%s data=%s msg=%s" % (r.get("success"), r.get("data"), r.get("msg")))
    assert r.get("success"), "分支B 应放行却被拦截: %s" % r
    created_ids.append(r.get("data"))
    print("B) 已到期放行 OK, destroyId=%s" % r.get("data"))

    # 分支 C：已销毁档案 → 拦截重复处理
    sql("UPDATE op_archive SET archive_status='DISPOSED', status='DESTROYED' WHERE id=%d;" % AID)
    r = call("POST", "/tenant/compliance/archive-destroy/create", token=tok,
             body={"archiveIds": [AID], "reason": "分支C-已销毁", "applicantId": 1})
    msg = str(r.get("msg") or "")
    print("C) 已销毁档案 → success=%s msg=%s" % (r.get("success"), msg))
    assert (not r.get("success")) and "已销毁" in msg, "分支C 未拦截: %s" % r
    print("C) 重复销毁拦截 OK")

    print("[SMOKE] 档案销毁到期校验三分支 PASS")
finally:
    # 清理：恢复档案原始状态 + 删除测试销毁申请
    sql("UPDATE op_archive SET destroy_date=NULL, archive_status='ARCHIVED', status='FILED', "
        "destroyed_at=NULL, integrity_status='UNKNOWN' WHERE id=%d;" % AID)
    for did in created_ids:
        if did:
            sql("DELETE FROM archive_destroy_approval WHERE id=%s;" % did)
    print("清理完成: 档案 %d 已恢复，测试销毁申请 %s 已删除" % (AID, created_ids))
