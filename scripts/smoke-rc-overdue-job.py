# -*- coding: utf-8 -*-
"""RcOverdueJob 链路验证（造逾期应收 → 打卡 OVERDUE → 生成催收待办 → 清理）。
Job 内部正是依次调用 accrueOverdueBadDebt() 与 createOverdueTodos(0)，
本脚本在真实租户上下文下跑通同一链路，等价于验证 Job 主体可用。
"""
import json, time, urllib.request, urllib.error, urllib.parse, sys, os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_env

BASE = smoke_env.base_url()
BILL_NO = "RC-SMK-JOB-%d" % int(time.time())


def sql(s, fetch=False):
    return smoke_env.run_sql(s, fetch=fetch)


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
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"success": False, "msg": "HTTP %s" % e.code}


tok = call("POST", "/auth/login", smoke_env.login_payload())["data"]["token"]

rid = None
try:
    # 1) 造一条已到期未结清应收
    sql("INSERT INTO rc_receivable(tenant_id,bill_no,customer_id,bill_date,currency_code,"
        "amount,received_amount,remain_amount,due_date,status,period,del_flag) VALUES "
        "(1,'%s',1,'2026-01-01','CNY',1000.00,0.00,1000.00,'2026-01-01','NONE','2026-01',0);" % BILL_NO)
    row = sql("SELECT id,status FROM rc_receivable WHERE bill_no='%s';" % BILL_NO, fetch=True)
    rid = row.strip().split("\n")[-1].split("\t")[0]
    print("1) 造逾期应收 id=%s bill_no=%s 初始状态=NONE" % (rid, BILL_NO))

    # 2) 打卡 OVERDUE（Job 第 1 步）
    r1 = call("POST", "/tenant/receivable/accrueOverdue", token=tok)
    print("2) accrueOverdue → success=%s data=%s" % (r1.get("success"), r1.get("data")))
    assert r1.get("success") and (r1.get("data") or 0) >= 1, "打卡逾期失败: %s" % r1

    st = sql("SELECT status FROM rc_receivable WHERE id=%s;" % rid, fetch=True).strip().split("\n")[-1]
    print("   应收 %s 状态 → %s" % (rid, st))
    assert "OVERDUE" in st, "状态未打卡为 OVERDUE: %s" % st

    # 3) 生成催收待办（Job 第 2 步）
    r2 = call("POST", "/tenant/collect-todo/createOverdue", token=tok, params={"days": 0})
    print("3) createOverdue(days=0) → success=%s data=%s" % (r2.get("success"), r2.get("data")))
    assert r2.get("success") and (r2.get("data") or 0) >= 1, "生成催收待办失败: %s" % r2

    cnt = sql("SELECT COUNT(*) FROM rc_collect_todo WHERE receivable_id=%s;" % rid, fetch=True)
    print("   该应收对应催收待办数 = %s" % cnt.strip().split("\n")[-1])

    print("[SMOKE] RcOverdueJob 链路 PASS（打卡逾期 → 催收待办已打通）")
finally:
    if rid:
        sql("DELETE FROM rc_collect_todo WHERE receivable_id=%s;" % rid)
        sql("DELETE FROM rc_receivable WHERE id=%s;" % rid)
        print("清理完成: 应收 %s 及关联催收待办已删除" % rid)
