#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FSSC apflow/arflow E2E 冒烟（任务2/3）：五类服务申请+派工选单+组长转审、月末关账冻结、开票链、10类AR服务申请。"""
import json
import time
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8089"
RUN = time.strftime("%H%M%S")
TOKEN = ""
results = []


def call(method, path, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json; charset=utf-8",
                                          "Authorization": "Bearer " + TOKEN})
    try:
        with urllib.request.urlopen(req) as resp:
            d = json.loads(resp.read().decode("utf-8"))
            if isinstance(d, dict) and d.get("code") not in (0, None):
                raise AssertionError(f"{method} {path} -> {d.get('msg')}")
            return d.get("data") if isinstance(d, dict) else d
    except urllib.error.HTTPError as e:
        raise AssertionError(f"{method} {path} -> {e.code}: {e.read().decode('utf-8', 'replace')[:150]}") from e


def step(name, fn):
    try:
        out = fn()
        results.append(True)
        print(f"[PASS] {name}: {out}")
    except Exception as e:  # noqa: BLE001
        results.append(False)
        print(f"[FAIL] {name}: {e}")


# 登录
def login():
    global TOKEN
    req = urllib.request.Request(BASE + "/api/auth/login",
                                 data=json.dumps({"username": "admin", "password": "123456",
                                                  "subjectType": "TENANT", "tenantCode": "DEMO1"}).encode("utf-8"),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        d = json.loads(resp.read().decode("utf-8"))
        TOKEN = d["data"]["token"]
        return TOKEN[:12] + "..."


# ---------------- AP：五类服务申请 + 派工选单 + 组长转审 ----------------
ctx = {}


def ap_create_dispatch():
    r = call("POST", "/api/tenant/ap-request/create", {
        "reqType": "PLAN_PAYMENT", "companyId": 1, "vendorName": "FSSC供应商" + RUN,
        "paymentWay": "银行转账", "bankNo": "622200001234", "amount": 30000,
        "invoiceNo": "77000001-05", "paymentDate": "2026-09-14T00:00:00"})
    ctx["sr"] = r["id"]
    assert r["status"] == "PENDING_DISPATCH"
    r2 = call("POST", f"/api/tenant/ap-request/{ctx['sr']}/dispatch")
    assert r2["status"] == "DISPATCHED"
    if r2.get("handlerPool"):
        # 多负责人进选单池，选单认领
        assert "ap-k1" in r2["handlerPool"]
        r3 = call("POST", f"/api/tenant/ap-request/{ctx['sr']}/claim", {"user": "ap-k1"})
        assert r3["handler"] == "ap-k1" and not r3.get("handlerPool")
        return f"选单池{r2['handlerPool']} → 选单 ap-k1"
    assert r2.get("handler"), "无负责人且无选单池"
    return r2["handler"]


def ap_confirm_modify():
    r = call("POST", f"/api/tenant/ap-request/{ctx['sr']}/confirm",
             {"paymentWay": "支票", "bankNo": "622200005555"})
    assert r["status"] == "GROUP_REVIEW", r["status"]
    return r["modifyNote"][-30:]


def ap_review():
    r = call("POST", f"/api/tenant/ap-request/{ctx['sr']}/group-review",
             {"pass": True, "note": "同意改支票", "reviewer": "ap-leader"})
    assert r["status"] == "DISPATCHED", r["status"]
    r2 = call("POST", f"/api/tenant/ap-request/{ctx['sr']}/post")
    assert r2["status"] == "POSTED" and r2["voucherNo"]
    assert not str(r2["voucherNo"]).startswith("EBS"), f"凭证号仍为模拟: {r2['voucherNo']}"
    return f"过账 真实分录凭证 {r2['voucherNo']}"


step("FSSC登录(DEMO1租户)", login)
step("AP服务申请 创建→按付款方法派工", ap_create_dispatch)
step("AP服务申请 确认修改付款方式转组长", ap_confirm_modify)
step("AP服务申请 组长审核→过账", ap_review)

# ---------------- AP月末关账（冻结打通点7） ----------------
mc = {}


def mc_init_frozen():
    r = call("POST", "/api/tenant/ap-month-close/create",
             {"companyId": 1, "period": "2026-08", "groupLeader": "ap-leader"})
    mc["id"] = r["id"]
    assert r["scpFrozen"] == 1
    frozen = call("GET", "/api/tenant/ap-month-close/frozen?companyId=1&period=2026-08")
    assert frozen is True, frozen
    # 冻结期间过账被拦截
    r2 = call("POST", "/api/tenant/ap-request/create", {
        "reqType": "URGENT_PAYMENT", "companyId": 1, "vendorName": "冻结测试" + RUN,
        "paymentWay": "银行转账", "bankNo": "622200008888", "amount": 100,
        "invoiceNo": "77000201-09", "paymentDate": "2026-08-20T00:00:00"})
    call("POST", f"/api/tenant/ap-request/{r2['id']}/dispatch")
    blocked = False
    try:
        call("POST", f"/api/tenant/ap-request/{r2['id']}/post")
    except AssertionError as e:
        blocked = "冻结" in str(e) or "关账" in str(e)
    assert blocked, "冻结期间过账未被拦截"
    return "协同冻结生效（过账被拦截）"


def mc_items_close():
    call("POST", f"/api/tenant/ap-month-close/{mc['id']}/dispatch", {"members": "ap-k1,ap-k2"})
    for code in ["BIZ_CHECK", "OFFSET_CHECK", "RELATED_CHECK", "IT_CHECK"]:
        call("POST", f"/api/tenant/ap-month-close/{mc['id']}/complete-item",
             {"code": code, "ok": True, "note": "已平检", "handler": "ap-k1"})
    r = call("POST", f"/api/tenant/ap-month-close/{mc['id']}/close-period")
    assert r["status"] == "CLOSED" and r["scpFrozen"] == 0
    return "关账完成+协同恢复"


step("AP月末关账 发起+协同冻结生效(打通点7)", mc_init_frozen)
step("AP月末关账 四检查项+关账恢复", mc_items_close)

# ---------------- AR：开票链（对账→核对→调整→拆分→金税→回写→红字） ----------------
ar = {}


def ar_statement_flow():
    s = call("POST", "/api/tenant/ar-flow/statements", {
        "companyId": 1, "customerCode": "1000000009", "customerName": "FSSC客户" + RUN,
        "amount": 20000, "taxAmount": 2600, "importUser": "商务助理"})
    ar["st"] = s["id"]
    assert s["status"] == "IMPORTED"
    r = call("POST", f"/api/tenant/ar-flow/statements/{ar['st']}/check")
    assert r["status"] == "ABNORMAL" and r["diffReason"], r  # 无应收明细→异常
    r2 = call("POST", f"/api/tenant/ar-flow/statements/{ar['st']}/adjust", {"note": "EBS已调整"})
    assert r2["status"] == "RECONCILED"
    return "导入→核对(异常)→财务调整确认"


def ar_invoice_flow():
    bills = call("GET", "/api/tenant/ar-flow/statements")
    bill_no = [b["billNo"] for b in bills if b["id"] == ar["st"]][0]
    invs = call("POST", f"/api/tenant/ar-flow/pre-invoices/generate?reconBillNo={bill_no}", None)
    assert invs, "预制发票未生成"
    inv = invs[0]
    ar["inv"] = inv["id"]
    assert inv["preInvoiceNo"].startswith(bill_no), inv["preInvoiceNo"]
    assert inv["status"] == "TO_INVOICE", f"商务确认未启用时应变TO_INVOICE，实际{inv['status']}"
    call("POST", f"/api/tenant/ar-flow/pre-invoices/{inv['id']}/push-tax")  # GOLDEN_TAX未配置→模拟
    r = call("POST", f"/api/tenant/ar-flow/pre-invoices/{inv['id']}/writeback",
             {"invoiceNo": "26312" + RUN, "invoiceDate": "2026-09-14"})
    assert r["status"] == "INVOICED"
    call("POST", f"/api/tenant/ar-flow/pre-invoices/{inv['id']}/print")
    r2 = call("POST", f"/api/tenant/ar-flow/pre-invoices/{inv['id']}/red",
              {"redNoticeNo": "HZD-" + RUN})
    assert r2["status"] == "RED_INVOICED"
    return "拆分→模拟金税→回写→打印→红字 全链路"


step("AR对账单 导入→容差核对→财务调整", ar_statement_flow)
step("AR预制发票 拆分合并→金税→回写→红字", ar_invoice_flow)

# ---------------- AR：10类服务申请（组织映射+科目默认） ----------------
claim = {}


def ar_claim_flow():
    c = call("POST", "/api/tenant/ar-flow/claims", {
        "companyId": 1, "claimType": "CLAIM_IN", "customerCode": "1000000009",
        "customerName": "FSSC客户" + RUN, "ccy": "USD", "fxRate": 7.1, "amount": 1000,
        "taxRate": 16, "factoryCode": "2400", "reason": "质量索赔"})
    claim["id"] = c["id"]
    assert c["debitAccount"] == "60010101", c["debitAccount"]
    assert float(c["buAmount"]) == 7100.0, c["buAmount"]
    assert c["status"] == "EKP_REVIEW"
    # 补强断言1：EKP类型创建时已发起 Flowable 审批流（procInstId 非空）
    assert c.get("procInstId"), f"流程实例未发起: {c.get('procInstId')}"
    # 补强断言2：审批走 Flowable（completeByBill→ArClaimBizAction→PENDING_DISPATCH→自动派工DISPATCHED）
    c1 = call("POST", f"/api/tenant/ar-flow/claims/{claim['id']}/ekp-approve")
    assert c1["status"] == "DISPATCHED", f"审批后状态 {c1.get('status')}"
    call("POST", f"/api/tenant/ar-flow/claims/{claim['id']}/assign", {"user": "ap-k1"})
    r = call("POST", f"/api/tenant/ar-flow/claims/{claim['id']}/review")
    assert r["status"] == "POSTED" and r["voucherNo"], r
    assert not str(r["voucherNo"]).startswith("EBS"), f"凭证号仍为模拟: {r['voucherNo']}"
    r3 = call("POST", f"/api/tenant/ar-flow/claims/{claim['id']}/complete")
    assert r3["status"] == "COMPLETED"
    return f"科目60010101+折本位币7100→Flowable审批→审单过账 真实凭证 {r['voucherNo']}"


step("AR服务申请 创建(附件四/五默认)+全流程", ar_claim_flow)

print(f"\n===== FSSC apflow/arflow 冒烟: {sum(results)}/{len(results)} PASS =====")
raise SystemExit(0 if all(results) else 1)
