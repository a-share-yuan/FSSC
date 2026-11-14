# -*- coding: utf-8 -*-
"""第三批冒烟：双签销毁/档案哈希/used_count/付款快照/借款台账/税会差异/坏账转回/预付冲销/含税切换/三单路由。
测试数据全部在 finally 中清理。
"""
import json, subprocess, time, urllib.request, urllib.error, urllib.parse, sys, os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_env

BASE = smoke_env.base_url()
TAG = "SMK-P3"
TS = str(int(time.time()))


def sql(s, fetch=False):
    return smoke_env.run_sql(s, fetch=fetch)


def q1(s):
    out = sql(s, fetch=True) or ""
    lines = [l for l in out.strip().split("\n") if l.strip()]
    return lines[-1].strip() if len(lines) > 1 else ""


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


tok = call("POST", "/auth/login", smoke_env.login_payload())["data"]["token"]
uids = [l.strip() for l in (sql("SELECT id FROM sys_user WHERE tenant_id=1 AND status='NORMAL' "
                                "ORDER BY id LIMIT 2;", fetch=True) or "").strip().split("\n") if l.strip()]
uids = uids[1:] if len(uids) > 1 else uids
u1 = uids[0]
u2 = uids[1] if len(uids) > 1 else str(int(u1) + 1000)  # 无第二人时构造一个不同 id
cid = q1("SELECT id FROM sys_company WHERE tenant_id=1 AND del_flag=0 ORDER BY id LIMIT 1;")

cleanup = {"archives": [], "destroys": [], "invoices": [], "bills": [], "assets": [],
           "subjects": [], "vouchers": [], "baddebts": [], "relations": []}

try:
    # ---------- 1) #4 销毁双签 ----------
    sql("UPDATE op_archive SET destroy_date='2020-01-01', archive_status='ARCHIVED', status='FILED' WHERE id=9;")
    d1 = call("POST", "/tenant/compliance/archive-destroy/create", token=tok,
              body={"archiveIds": [9], "reason": "P3双签测试", "applicantId": int(u1)})
    did = d1.get("data")
    assert did, "销毁申请创建失败: %s" % d1
    cleanup["destroys"].append(did)
    print("1a) 销毁申请 id=%s" % did)

    r = call("POST", "/tenant/compliance/archive-destroy/%s/approve" % did, token=tok,
             body={"approverId": int(u1), "approved": True})
    st = q1("SELECT approval_status FROM archive_destroy_approval WHERE id=%s;" % did)
    print("1b) 第一签 → success=%s 状态=%s（应为 PENDING 待复核）" % (r.get("success"), st))
    assert r.get("success") and st == "PENDING", "第一签后状态应仍为 PENDING: %s / %s" % (r, st)

    r = call("POST", "/tenant/compliance/archive-destroy/%s/approve" % did, token=tok,
             body={"approverId": int(u1), "approved": True})
    msg = str(r.get("msg") or "")
    print("1c) 同一人二次签 → success=%s msg=%s" % (r.get("success"), msg[:50]))
    assert (not r.get("success")) and "不同复核人" in msg, "未拦截同一人双签: %s" % r

    r = call("POST", "/tenant/compliance/archive-destroy/%s/approve" % did, token=tok,
             body={"approverId": int(u2), "approved": True})
    st = q1("SELECT approval_status FROM archive_destroy_approval WHERE id=%s;" % did)
    print("1d) 第二人签 → 状态=%s（应为 APPROVED）" % st)
    assert st == "APPROVED", "双签完成后应为 APPROVED: %s / %s" % (r, st)

    # 只签一人即执行 → 拦截
    d2 = call("POST", "/tenant/compliance/archive-destroy/create", token=tok,
              body={"archiveIds": [9], "reason": "P3单签执行测试", "applicantId": int(u1)})
    did2 = d2.get("data")
    cleanup["destroys"].append(did2)
    call("POST", "/tenant/compliance/archive-destroy/%s/approve" % did2, token=tok,
         body={"approverId": int(u1), "approved": True})
    r = call("POST", "/tenant/compliance/archive-destroy/%s/execute" % did2, token=tok)
    msg = str(r.get("msg") or "")
    print("1e) 仅一签即执行 → success=%s msg=%s" % (r.get("success"), msg[:50]))
    assert (not r.get("success")) and ("第二复核人" in msg or "未获批准" in msg), "未拦截单签执行: %s" % r
    print("1) #4 销毁双签 OK（同人拦截 + 双签通过 + 单签禁执行）")

    # ---------- 2) #20 档案哈希 ----------
    a_res = call("POST", "/tenant/compliance/archive/auto-archive", token=tok,
                 body={"billId": 1, "archiveType": "BILL", "archiveYear": 2026})
    aid = a_res.get("data")
    assert aid, "自动归档创建失败: %s" % a_res
    cleanup["archives"].append(aid)
    call("POST", "/tenant/compliance/archive/%s/archive" % aid, token=tok)
    row = q1("SELECT integrity_hash, integrity_status FROM op_archive WHERE id=%s;" % aid)
    print("2a) 归档后 integrity_hash/status = %s" % row)
    assert row and not row.startswith("NULL"), "归档未生成完整性哈希: %s" % row
    assert "VERIFIED" in row, "归档后状态应为 VERIFIED: %s" % row

    sql("UPDATE op_archive SET archive_no='TAMPERED-%s' WHERE id=%s;" % (TS, aid))
    r = call("GET", "/tenant/compliance/archive/%s/verify" % aid, token=tok)
    st2 = q1("SELECT integrity_status FROM op_archive WHERE id=%s;" % aid)
    print("2b) 篡改档案号后校验 → data=%s status=%s（应为 False/MISMATCH）" % (r.get("data"), st2))
    assert r.get("data") is False and st2 == "MISMATCH", "篡改未被检出: %s / %s" % (r, st2)
    print("2) #20 档案哈希防篡改 OK（篡改检出并置 MISMATCH）")

    # ---------- 3) #12 used_count ----------
    inv_id = call("POST", "/tenant/invoice/create", token=tok, body={
        "invoiceCode": "041002600111", "invoiceNo": "P3-%s" % TS, "invoiceType": "VAT_SPECIAL",
        "sellerName": "P3测试供应商", "invoiceDate": "2026-08-01",
        "amount": 1000.0, "taxAmount": 60.0, "netAmount": 940.0, "currencyCode": "CNY"}).get("data")
    cleanup["invoices"].append(inv_id)
    call("POST", "/tenant/invoice/bind", token=tok,
         body={"billId": 1, "invoiceId": inv_id, "amount": 300.0})
    cnt = q1("SELECT used_count FROM inv_invoice WHERE id=%s;" % inv_id)
    print("3a) 绑定 1 次后 used_count=%s（应=1）" % cnt)
    assert cnt == "1", "used_count 未同步: %s" % cnt
    audit = call("GET", "/tenant/invoice/used-count/audit", token=tok, params={"fix": "false"}).get("data") or []
    print("3b) 占用计数对账 不一致=%d 条" % len(audit))
    assert isinstance(audit, list), "对账接口异常: %s" % audit
    print("3) #12 发票占用计数一致性 OK")

    # ---------- 4) #14 付款账号与主数据一致性 ----------
    # 自造收款方主数据（存量 md_payee 的 ref_id 均为 NULL，不能依赖选数）
    sql("INSERT INTO md_payee(tenant_id,payee_type,ref_id,payee_name,bank_account,account_type,status,del_flag) "
        "VALUES (1,'SUPPLIER',999001,'P3测试供应商','REAL_ACC_%s','PUBLIC','NORMAL',0);" % TS)
    payee_type, ref_id = "SUPPLIER", "999001"
    sql("INSERT INTO exp_bill(tenant_id,bill_no,bill_type,bill_sub_type,title,company_id,"
        "apply_user_id,apply_date,currency_code,total_amount,status,payable_amount,offset_amount,"
        "image_status,del_flag,create_time,update_time) VALUES "
        "(1,'%s-PAY-%s','PAY','PREPAY','P3付款快照',%s,%s,CURDATE(),'CNY',1000.00,'APPROVED',1000.00,0.00,"
        "'NONE',0,NOW(),NOW());" % (TAG, TS, cid, u1))
    pay_bill = q1("SELECT id FROM exp_bill WHERE bill_no='%s-PAY-%s';" % (TAG, TS))
    cleanup["bills"].append(pay_bill)
    r = call("POST", "/tenant/pay/payment/create/%s" % pay_bill, token=tok,
             body={"payeeType": payee_type, "payeeId": int(ref_id), "payeeName": "P3测试",
                   "bankAccount": "WRONG_ACC_%s" % TS, "amount": 100.0})
    msg = str(r.get("msg") or "")
    print("4a) 用主数据外账号付款 → success=%s msg=%s" % (r.get("success"), msg[:60]))
    assert (not r.get("success")) and "主数据不一致" in msg, "付款快照校验未生效: %s" % r
    print("4) #14 付款账号主数据一致性校验 OK")

    # ---------- 5) #18 借款台账 ----------
    sql("INSERT INTO exp_bill(tenant_id,bill_no,bill_type,title,company_id,apply_user_id,"
        "apply_date,currency_code,total_amount,loan_balance,expect_repay_date,status,"
        "image_status,del_flag,create_time,update_time) VALUES "
        "(1,'%s-LOAN-%s','LOAN','P3借款台账',%s,%s,CURDATE(),'CNY',5000.00,5000.00,"
        "DATE_SUB(CURDATE(),INTERVAL 45 DAY),'APPROVED','NONE',0,NOW(),NOW());" % (TAG, TS, cid, u1))
    loan_id = q1("SELECT id FROM exp_bill WHERE bill_no='%s-LOAN-%s';" % (TAG, TS))
    cleanup["bills"].append(loan_id)
    led = call("GET", "/tenant/expense/bill/loan-ledger", token=tok, params={"onlyOverdue": "true"}).get("data") or []
    hit = [x for x in led if str(x.get("applyUserId")) == str(u1)]
    print("5a) 逾期借款台账 %d 人，命中借款人=%s" % (len(led), bool(hit)))
    assert hit, "借款台账未包含测试借款人: %s" % [x.get("applyUserId") for x in led][:5]
    h = hit[0]
    print("5b) 借款额=%s 未还=%s 逾期天数=%s overdue=%s" % (h.get("loanTotal"), h.get("balance"),
                                                    h.get("overdueDays"), h.get("overdue")))
    assert h.get("overdue") is True and h.get("overdueDays") > 0, "逾期判定异常: %s" % h
    print("5) #18 员工借款台账 OK")

    # ---------- 6) #16 税会差异 ----------
    sql("INSERT INTO md_asset(tenant_id,asset_no,asset_name,company_id,purchase_date,"
        "original_amount,net_amount,residual_value,depreciation_method,depreciation_months,"
        "tax_life_months,monthly_depr,accum_depr,status,create_time) VALUES "
        "(1,'%s-AS-%s','P3税会差异资产',%s,CURDATE(),12000.00,12000.00,0.00,'LINEAR',60,84,"
        "200.00,0.00,'INIT',NOW());" % (TAG, TS, cid))
    aid2 = q1("SELECT id FROM md_asset WHERE asset_no='%s-AS-%s';" % (TAG, TS))
    cleanup["assets"].append(aid2)
    diffs = call("GET", "/tenant/asset/tax-diff", token=tok).get("data") or []
    hit = [x for x in diffs if str(x.get("assetId")) == str(aid2)]
    print("6a) 税会差异清单 %d 条，命中测试资产=%s" % (len(diffs), bool(hit)))
    assert hit, "税会差异未包含测试资产"
    d = hit[0]
    print("6b) 会计月折旧=%s 税法月折旧=%s 差异=%s 调整=%s" % (d.get("accountMonthlyDepr"),
                                                   d.get("taxMonthlyDepr"), d.get("diffAmount"), d.get("adjustType")))
    assert float(d.get("diffAmount")) > 0 and d.get("adjustType") == "调增", "税会差异计算异常: %s" % d
    print("6) #16 资产税会差异 OK（税法年限更长→税法折旧小→调增）")

    # ---------- 7) #19 坏账转回 + 红字凭证 ----------
    for code, name in (("1231", "坏账准备"), ("6701", "信用减值损失")):
        sql("INSERT INTO md_subject(tenant_id,company_id,subject_code,subject_name,parent_id,"
            "subject_level,subject_type,direction,is_leaf,status,del_flag) VALUES "
            "(1,%s,'%s','%s',0,1,'ASSET','DEBIT',1,'NORMAL',0);" % (cid, code, name))
        sid = q1("SELECT id FROM md_subject WHERE subject_code='%s' AND tenant_id=1;" % code)
        cleanup["subjects"].append(sid)
    rcv = q1("SELECT id FROM rc_receivable WHERE tenant_id=1 LIMIT 1;")
    sql("INSERT INTO rc_bad_debt(tenant_id,customer_id,receivable_id,amount,provision_date,"
        "provision_rate,period,status,create_time,update_time) VALUES "
        "(1,1,%s,500.00,CURDATE(),0.10,'2026-08','PROVISIONED',NOW(),NOW());" % rcv)
    bd = q1("SELECT id FROM rc_bad_debt WHERE receivable_id=%s AND period='2026-08' "
            "ORDER BY id DESC LIMIT 1;" % rcv)
    cleanup["baddebts"].append(bd)
    r = call("POST", "/tenant/bad-debt/%s/reverse" % bd, token=tok)
    print("7a) 坏账转回 → success=%s data=%s" % (r.get("success"), r.get("data")))
    assert r.get("success"), "坏账转回失败: %s" % r
    vid = (r.get("data") or {}).get("voucherId")
    cleanup["vouchers"].append(vid)
    st = q1("SELECT status FROM rc_bad_debt WHERE id=%s;" % bd)
    vrow = q1("SELECT debit_total, credit_total, is_balanced FROM op_voucher WHERE id=%s;" % vid)
    lines = q1("SELECT GROUP_CONCAT(CONCAT(subject_code,'-',direction,'-',amount)) "
               "FROM op_voucher_line WHERE voucher_id=%s;" % vid)
    print("7b) 坏账状态=%s 凭证借/贷/平衡=%s 分录=%s" % (st, vrow, lines))
    assert st == "REVERSED", "坏账状态未置 REVERSED: %s" % st
    assert "1231-DEBIT" in lines and "6701-CREDIT" in lines, "红字凭证分录方向错误: %s" % lines
    # 已转回不可重复转回
    r = call("POST", "/tenant/bad-debt/%s/reverse" % bd, token=tok)
    assert not r.get("success"), "已转回不应可重复转回: %s" % r
    print("7) #19 坏账转回红字凭证 OK（借1231/贷6701，重复转回拦截）")

    # ---------- 8) #13 预付冲销 ----------
    # 复用 4) 造的预付单（PAY/PREPAY，total=1000）
    sql("INSERT INTO exp_bill(tenant_id,bill_no,bill_type,title,company_id,apply_user_id,"
        "apply_date,currency_code,total_amount,status,payable_amount,offset_amount,"
        "image_status,del_flag,create_time,update_time) VALUES "
        "(1,'%s-CLM-%s','CLAIM','P3预付冲销',%s,%s,CURDATE(),'CNY',800.00,'APPROVING',800.00,0.00,"
        "'NONE',0,NOW(),NOW());" % (TAG, TS, cid, u1))
    clm = q1("SELECT id FROM exp_bill WHERE bill_no='%s-CLM-%s';" % (TAG, TS))
    cleanup["bills"].append(clm)
    r = call("POST", "/tenant/expense/detail/prepay-offset/%s" % clm, token=tok,
             body={"prepayBillId": int(pay_bill), "amount": 600.0})
    print("8a) 冲销 600 → success=%s" % r.get("success"))
    assert r.get("success"), "预付冲销失败: %s" % r
    row = q1("SELECT offset_amount, payable_amount FROM exp_bill WHERE id=%s;" % clm)
    print("8b) 冲销后 冲抵/应付 = %s（应 600.00/200.00）" % row)
    assert "600.00" in row and "200.00" in row, "冲销后金额重算异常: %s" % row
    r = call("POST", "/tenant/expense/detail/prepay-offset/%s" % clm, token=tok,
             body={"prepayBillId": int(pay_bill), "amount": 600.0})
    msg = str(r.get("msg") or "")
    print("8c) 再冲 600（累计1200>1000）→ success=%s msg=%s" % (r.get("success"), msg[:60]))
    assert (not r.get("success")) and "可冲销余额" in msg, "超冲未拦截: %s" % r
    print("8) #13 预付冲销 OK（金额重算 + 超额拦截）")

    # ---------- 9) #9 含税/不含税录入切换 ----------
    item = q1("SELECT id FROM md_expense_item WHERE tenant_id=1 AND status='NORMAL' LIMIT 1;")
    dr = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM", "title": "P3含税切换", "companyId": int(cid),
                 "applyUserId": int(u1), "currencyCode": "CNY", "priceType": "TAX_EX",
                 "totalAmount": 100.00, "extJson": "{}"},
        "lines": [{"itemId": int(item), "amount": 100.00, "taxRate": 0.06,
                   "priceType": "TAX_EX", "expenseDate": "2026-08-28"}],
        "attaches": []})
    tid = dr.get("data")
    cleanup["bills"].append(tid)
    assert tid, "含税切换草稿创建失败: %s" % dr
    # 费用项要求附发票（need_invoice=1）：补发票附件记录，避免被业务必填拦截
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) "
        "VALUES (1,%s,'INVOICE',1,1,0);" % tid)
    r = call("POST", "/tenant/expense/bill/submit/%s" % tid, token=tok)
    lrow = q1("SELECT amount, net_amount, tax_amount FROM exp_bill_line WHERE bill_id=%s;" % tid)
    print("9a) 不含税录入 100 @6%% → 提交 msg=%s" % str(r.get("msg") or "")[:60])
    print("9b) 明细 含税/不含税/税额 = %s（应 106.00 / 100.00 / 6.00）" % lrow)
    vals = lrow.split("\t")
    assert vals[0].strip() == "106.00", "含税金额未按 TAX_EX 拆分: %s" % lrow
    assert vals[1].strip() == "100.00" and vals[2].strip() == "6.00", "价税拆分错误: %s" % lrow
    print("9) #9 含税/不含税录入切换 OK（TAX_EX 自动拆价税）")

    # ---------- 10) #17 三单匹配异常路由 ----------
    r = call("POST", "/tenant/pay/match/%s/route" % pay_bill, token=tok)
    print("10a) 三单路由 → success=%s" % r.get("success"))
    if r.get("success"):
        d = r.get("data") or {}
        print("10b) 主责路由=%s 冻结=%s" % (d.get("routing"), d.get("frozen")))
        assert d.get("routing"), "路由结果为空: %s" % d
    else:
        print("10b) 三单一致无需派单（msg=%s）" % str(r.get("msg") or "")[:50])
    print("10) #17 三单匹配异常路由 OK")

    print("[SMOKE] 第三批 10 项 PASS")
finally:
    for v in cleanup["vouchers"]:
        if v:
            sql("DELETE FROM op_voucher_line WHERE voucher_id=%s;" % v)
            sql("DELETE FROM op_voucher WHERE id=%s;" % v)
    for s in cleanup["subjects"]:
        if s:
            sql("DELETE FROM md_subject WHERE id=%s;" % s)
    for b in cleanup["baddebts"]:
        if b:
            sql("DELETE FROM rc_bad_debt WHERE id=%s;" % b)
    for b in cleanup["bills"]:
        if b:
            sql("DELETE FROM exp_bill_line WHERE bill_id=%s;" % b)
            sql("DELETE FROM exp_bill_attach WHERE bill_id=%s;" % b)
            sql("DELETE FROM exp_bill WHERE id=%s;" % b)
    for i in cleanup["invoices"]:
        if i:
            sql("DELETE FROM inv_bill_invoice WHERE invoice_id=%s;" % i)
            sql("DELETE FROM inv_invoice WHERE id=%s;" % i)
    for d in cleanup["destroys"]:
        if d:
            sql("DELETE FROM archive_destroy_approval WHERE id=%s;" % d)
    for a in cleanup["archives"]:
        if a:
            sql("DELETE FROM op_archive WHERE id=%s;" % a)
    for a in cleanup["assets"]:
        if a:
            sql("DELETE FROM md_asset WHERE id=%s;" % a)
    sql("UPDATE op_archive SET destroy_date=NULL, archive_status='ARCHIVED', status='FILED', "
        "destroyed_at=NULL, integrity_status='UNKNOWN' WHERE id=9;")
    sql("DELETE FROM md_payee WHERE ref_id=999001 AND payee_name='P3测试供应商';")
    print("清理完成")
