# -*- coding: utf-8 -*-
"""第二批冒烟：核定金额三层 / 收款账户校验 / 跨组织与代理报销字段落库。
测试数据全部在 finally 中清理。
"""
import json, subprocess, time, urllib.request, urllib.error, urllib.parse

BASE = "http://127.0.0.1:18080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
TAG = "SMK-P2-%d" % int(time.time())


def sql(s, fetch=False):
    p = subprocess.run([MYSQL, "-h192.168.20.77", "-uroot", "-p123456", "fssc",
                        "--default-character-set=utf8mb4", "-e", s],
                       capture_output=True, text=True, encoding="utf-8", errors="ignore")
    return p.stdout if fetch else None


def q1(s):
    out = sql(s, fetch=True) or ""
    lines = [l for l in out.strip().split("\n") if l.strip()]
    return lines[-1] if len(lines) > 1 else ""


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
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"success": False, "msg": "HTTP %s" % e.code}


tok = call("POST", "/auth/login", {"username": "alice", "password": "123456",
                                   "tenantCode": "DEMO1", "subjectType": "TENANT"})["data"]["token"]
uid = q1("SELECT id FROM sys_user WHERE tenant_id=1 AND status='NORMAL' LIMIT 1;").strip()
# 取两家不同公司，验证跨组织「承担单位 ≠ 结算单位」确实能分离
cid = q1("SELECT id FROM sys_company WHERE tenant_id=1 AND del_flag=0 ORDER BY id LIMIT 1;").strip()
comp2 = q1("SELECT id FROM sys_company WHERE tenant_id=1 AND del_flag=0 ORDER BY id LIMIT 1 OFFSET 1;").strip()
assert cid and comp2 and cid != comp2, "需要至少两家不同公司才能验证跨组织分离: cid=%s comp2=%s" % (cid, comp2)

bill_id = None
draft_id = None
try:
    # ---------- 1) 核定金额三层 ----------
    sql("INSERT INTO exp_bill(tenant_id,bill_no,bill_type,title,company_id,apply_user_id,"
        "apply_date,currency_code,total_amount,status,payable_amount,offset_amount,"
        "image_status,del_flag,create_time,update_time) VALUES "
        "(1,'%s','CLAIM','冒烟-核减测试',%s,%s,CURDATE(),'CNY',2000.00,'SUBMITTED',2000.00,0.00,"
        "'NONE',0,NOW(),NOW());" % (TAG, cid, uid))
    bill_id = q1("SELECT id FROM exp_bill WHERE bill_no='%s';" % TAG).strip()
    print("1a) 造待审批单据 id=%s 申请金额=2000.00" % bill_id)

    cases = [
        ("核增拦截", {"approvedAmount": 3000, "approvedPayAmount": 3000, "approvedRefundAmount": 0}, "超出申请金额"),
        ("付款超核定拦截", {"approvedAmount": 1000, "approvedPayAmount": 1500, "approvedRefundAmount": 0}, "超出核定报销"),
        ("退款不等拦截", {"approvedAmount": 1000, "approvedPayAmount": 800, "approvedRefundAmount": 100}, "核定退款"),
        ("全空拦截", {}, "核定金额不能全为空"),
    ]
    for name, body, kw in cases:
        r = call("POST", "/tenant/expense/bill/approve-deduct/%s" % bill_id, token=tok, body=body)
        msg = str(r.get("msg") or "")
        ok = (not r.get("success")) and kw in msg
        print("1b) %-14s → success=%s msg=%s" % (name, r.get("success"), msg[:70]))
        assert ok, "%s 未被拦截: %s" % (name, r)
    print("1) 核定三层负向校验 OK")

    # 正常核减
    r = call("POST", "/tenant/expense/bill/approve-deduct/%s" % bill_id, token=tok,
             body={"approvedAmount": 1000, "approvedPayAmount": 800, "approvedRefundAmount": 200,
                   "remark": "冒烟核减"})
    print("1c) 正常核减 → success=%s msg=%s" % (r.get("success"), r.get("msg")))
    assert r.get("success"), "正常核减失败: %s" % r
    row = q1("SELECT approved_amount,approved_pay_amount,approved_refund_amount,payable_amount "
             "FROM exp_bill WHERE id=%s;" % bill_id)
    print("1d) 落库 核定报销/核定付款/核定退款/应付 = %s" % row)
    assert "1000.00" in row and "800.00" in row and "200.00" in row, "核定字段落库异常: %s" % row
    assert row.strip().endswith("800.00"), "应付未按核定付款重算: %s" % row
    print("1) 核定三层正向 OK（应付随核定付款重算为 800）")

    # ---------- 2) 收款账户校验（payTarget 驱动） ----------
    item = q1("SELECT id FROM md_expense_item WHERE tenant_id=1 AND status='NORMAL' LIMIT 1;").strip()
    draft = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM", "title": "冒烟-收款账户", "companyId": int(cid),
                 "applyUserId": int(uid), "currencyCode": "CNY", "payTarget": "SUPPLIER",
                 "totalAmount": 100.00,  # 与明细合计一致，先过 R1 才能验证到收款账户校验
                 "settleCompanyId": int(comp2), "payCompanyId": int(cid), "receiverUserId": int(uid),
                 "extJson": json.dumps({"paperAttachCount": 0})},  # extJson 实体为 String，须传 JSON 字符串
        "lines": [{"itemId": int(item), "amount": 100.00, "expenseDate": "2026-08-28"}],
        "attaches": []})
    draft_id = draft.get("data")
    print("2a) 造草稿 id=%s（payTarget=SUPPLIER 且无收款账号）" % draft_id)
    assert draft_id, "草稿创建失败: %s" % draft

    r = call("POST", "/tenant/expense/bill/submit/%s" % draft_id, token=tok)
    msg = str(r.get("msg") or "")
    print("2b) 无账号提交 → success=%s msg=%s" % (r.get("success"), msg[:80]))
    assert (not r.get("success")) and "收款账号必填" in msg, "收款账户校验未生效: %s" % r
    print("2) 收款账户必填校验 OK")

    # ---------- 3) 跨组织与代理报销字段落库 ----------
    row = q1("SELECT settle_company_id,pay_company_id,receiver_user_id,pay_target "
             "FROM exp_bill WHERE id=%s;" % draft_id)
    print("3) 跨组织字段落库 结算单位/支付单位/收款人/收款对象 = %s（承担单位=%s，结算单位应=%s）" % (row, cid, comp2))
    vals = row.split("\t")
    assert vals[0].strip() == comp2, "settle_company_id 未落库: %s" % row
    assert vals[1].strip() == cid, "pay_company_id 未落库: %s" % row
    assert vals[0].strip() != vals[1].strip(), "结算单位与支付单位相同，未能体现跨组织分离: %s" % row
    assert vals[2].strip() == uid, "receiver_user_id 未落库: %s" % row
    assert vals[3].strip() == "SUPPLIER", "pay_target 未落库: %s" % row
    print("3) 跨组织四维度 + 代理报销字段落库 OK（结算单位 %s ≠ 支付单位 %s，分离生效）" % (comp2, cid))

    print("[SMOKE] 第二批（核定三层/收款账户/跨组织）PASS")
finally:
    if bill_id:
        sql("DELETE FROM exp_bill WHERE id=%s;" % bill_id)
    if draft_id:
        sql("DELETE FROM exp_bill_line WHERE bill_id=%s;" % draft_id)
        sql("DELETE FROM exp_bill WHERE id=%s;" % draft_id)
    print("清理完成: 测试单据 %s / %s 已删除" % (bill_id, draft_id))
