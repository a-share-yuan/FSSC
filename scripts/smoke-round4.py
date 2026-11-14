# -*- coding: utf-8 -*-
# 本轮冒烟：①申请单关闭/终止报销(释放预算) ②对公支付 PAY 全链路(合同/客户/付款记录) ③新报表接口
import json, urllib.request, urllib.error, urllib.parse, uuid

BASE = "http://127.0.0.1:18080/api"

def call(method, path, body=None, token=None, params=None, raw=None, ctype=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", ctype or "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

def upload_invoice(token):
    png = bytes.fromhex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
        "0000000d49444154789c626001000000ffff03000006000557bfabd40000000049454e44ae426082")
    boundary = "----wb" + uuid.uuid4().hex
    parts = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"inv.png\"\r\nContent-Type: image/png\r\n\r\n".encode() + png + b"\r\n",
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"bizType\"\r\n\r\nINVOICE\r\n".encode(),
        f"--{boundary}--\r\n".encode(),
    ]
    r = call("POST", "/tenant/system/file/upload", token=token, raw=b"".join(parts), ctype=f"multipart/form-data; boundary={boundary}")
    assert r.get("success"), r
    return r["data"]

token = call("POST", "/auth/login", {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"})["data"]["token"]
print("1) LOGIN OK")

items = call("GET", "/tenant/masterdata/expense-item/tree", token=token).get("data") or []
it = items[0]
bg = (call("GET", "/tenant/budget/item/page", token=token, params={"pageNum": 1, "pageSize": 50}).get("data") or {}).get("list") or []
bg_item = bg[0] if bg else None
cust = (call("GET", "/tenant/customer/page", token=token, params={"pageNum": 1, "pageSize": 10}).get("data") or {}).get("list") or []
cont = (call("GET", "/tenant/contract/page", token=token, params={"pageNum": 1, "pageSize": 10}).get("data") or {}).get("list") or []
assert cust and cont, "演示供应商/合同缺失"
c = cust[0]; ct = cont[0]
print(f"2) 主数据 OK: customer={c['customerName']} contract={ct['contractNo']}")

def approve(bid):
    det = call("GET", f"/tenant/expense/bill/detail/{bid}", token=token)
    proc = (det.get("data") or {}).get("bill", {}).get("procInstId")
    # 多级审批流：逐级 complete 直到流程结束
    done = False
    for _ in range(10):
        todo = call("GET", "/tenant/wf/todo", token=token).get("data") or []
        t = next((x for x in todo if str(x.get("processInstanceId")) == str(proc)), None)
        if not t:
            done = True
            break
        r = call("POST", "/tenant/wf/approve", token=token, body={"taskId": t["taskId"], "approved": True, "comment": "同意"})
        assert r.get("success"), r
    assert done, f"多级审批未走完 proc={proc}"


def new_bill(bill, lines):
    d = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={"bill": bill, "lines": lines, "attaches": []})
    assert d.get("success"), d
    return d["data"]

def line(amt):
    return [{"itemId": it["id"], "itemName": it.get("itemName", ""), "amount": amt, "taxRate": 0, "taxAmount": 0,
             "netAmount": amt, "budgetItemId": bg_item["id"] if bg_item else None, "expenseDate": "2026-08-12"}]

# ============ A. 申请单关闭（释放预算） ============
bid = new_bill({"billType": "APPLY", "billSubType": "MEETING", "title": "关闭测试-会议申请", "companyId": 1, "deptId": 1,
                "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 1000,
                "extJson": json.dumps({"meetingName": "关闭测试"}, ensure_ascii=False), "remark": "关闭测试"}, line(1000))
assert call("POST", f"/tenant/expense/bill/submit/{bid}", token=token).get("success")
approve(bid)
r = call("POST", f"/tenant/expense/bill/close/{bid}", token=token)
assert r.get("success"), r
pg = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "APPLY"})
row = next((x for x in (pg.get("data") or {}).get("list") or [] if x.get("id") == bid), None)
print(f"3) 申请单关闭 OK -> {row.get('status')} (预算已释放)")
assert row and row.get("status") == "CLOSED"

# ============ B. 申请单终止报销 ============
bid2 = new_bill({"billType": "APPLY", "billSubType": "MEETING", "title": "终止测试-会议申请", "companyId": 1, "deptId": 1,
                 "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 800,
                 "extJson": json.dumps({"meetingName": "终止测试"}, ensure_ascii=False), "remark": "终止测试"}, line(800))
assert call("POST", f"/tenant/expense/bill/submit/{bid2}", token=token).get("success")
approve(bid2)
r2 = call("POST", f"/tenant/expense/bill/terminate/{bid2}", token=token)
assert r2.get("success"), r2
pg2 = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "APPLY"})
row2 = next((x for x in (pg2.get("data") or {}).get("list") or [] if x.get("id") == bid2), None)
print(f"4) 申请单终止报销 OK -> {row2.get('status')}")
assert row2 and row2.get("status") == "TERMINATED"

# ============ C. 对公支付（预付款 PAY/PREPAY 关联合同+客户） ============
inv = upload_invoice(token)
attach = [{"fileId": inv["id"], "fileName": inv.get("fileName", "inv.png"), "attachType": "INVOICE"}]
pay_bill = {"billType": "PAY", "billSubType": "PREPAY", "title": "预付示范供应商货款", "companyId": 1, "deptId": 1,
            "customerId": c["id"], "contractId": ct["id"], "payeeName": c["customerName"],
            "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 1000,
            "extJson": json.dumps({"payMethod": "BANK", "payeeAccountNo": "622202****8888", "payeeBankName": "招商银行"}, ensure_ascii=False),
            "remark": "对公预付款冒烟"}
d = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={"bill": pay_bill, "lines": line(1000), "attaches": attach})
assert d.get("success"), d
pay_id = d["data"]
assert call("POST", f"/tenant/expense/bill/submit/{pay_id}", token=token).get("success")
approve(pay_id)
pg3 = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "PAY"})
row3 = next((x for x in (pg3.get("data") or {}).get("list") or [] if x.get("id") == pay_id), None)
print(f"5) 对公支付(预付) 审批后: {row3.get('status')} {row3.get('billNo')}")
assert row3 and row3.get("status") == "APPROVED"
# 付款记录应已生成（WAITING，供出纳组批），且携带合同/客户关联
pmts = call("GET", f"/tenant/pay/payment/list/{pay_id}", token=token).get("data") or []
print(f"   付款记录: {len(pmts)} 条, 状态={[p.get('payStatus') for p in pmts][:3]}, 收款方={[p.get('payeeName') for p in pmts][:3]}")
assert pmts and pmts[0].get("payStatus") == "WAITING", pmts
assert pmts[0].get("customerId") == c["id"] and pmts[0].get("contractId") == ct["id"], "付款记录未携带合同/客户关联"
print("   付款记录已携带 customer_id/contract_id 关联 OK")

# ============ D. 新报表接口 ============
loan = call("GET", "/tenant/report/loan-balance", token=token).get("data") or []
cs = call("GET", "/tenant/report/contract-summary", token=token).get("data") or []
det = call("GET", "/tenant/report/expense-detail", token=token, params={"kw": "预"}).get("data") or []
print(f"6) 报表 OK: 借款余额汇总 {len(loan)} 行 / 合同汇总 {len(cs)} 行 / 费用明细(关键字'预') {len(det)} 行")
assert isinstance(loan, list) and isinstance(cs, list) and isinstance(det, list)
if cs:
    print(f"   合同汇总首行: {cs[0].get('contractName')} 总额={cs[0].get('contractAmount')} 已付={cs[0].get('paidAmount')}")

print("\n[SMOKE] 关闭/终止/对公支付/报表 全链路 PASS")
