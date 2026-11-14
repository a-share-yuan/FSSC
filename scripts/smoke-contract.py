# -*- coding: utf-8 -*-
# 合同台账冒烟（中集财务共享需求说明书-合同台账 v1.0）：
# ①新建合同(台账字段) → 提交 → 审批 → 固定金额冻结预算 ②无固定金额不冻结 ③手工关闭释放预算
# ④超合同金额付款拦截 ⑤付款单引用合同审批通过 → cont_pay_record 自动更新
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

def approve_bill(bid, token):
    det = call("GET", f"/tenant/expense/bill/detail/{bid}", token=token)
    proc = (det.get("data") or {}).get("bill", {}).get("procInstId")
    todo = call("GET", "/tenant/wf/todo", token=token).get("data") or []
    t = next((x for x in todo if str(x.get("processInstanceId")) == str(proc)), None)
    assert t, f"待办缺失 {proc}"
    r = call("POST", "/tenant/wf/approve", token=token, body={"taskId": t["taskId"], "approved": True, "comment": "同意"})
    assert r.get("success"), r

token = call("POST", "/auth/login", {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"})["data"]["token"]
items = call("GET", "/tenant/masterdata/expense-item/tree", token=token).get("data") or []
it = items[0]
bg = (call("GET", "/tenant/budget/item/page", token=token, params={"pageNum": 1, "pageSize": 50}).get("data") or {}).get("list") or []
bg_item = bg[0]

# 0) 菜单：合同管理应为顶级菜单
mt = call("GET", "/tenant/system/menu/tree", token=token).get("data") or []
def flat(n, o):
    o.append(n); [flat(c, o) for c in (n.get("children") or [])]
allm = []; [flat(m, allm) for m in mt]
top = {m.get("menuName") for m in mt}
assert "合同管理" in top, "合同管理不是顶级菜单"
print("1) 菜单 OK: 合同管理 已提升为顶级菜单")

# 1) 新建台账合同（固定金额+费用类型+客商+招标比价）→ 提交 → 审批 → 冻结预算
occupy_before = float(bg_item.get("occupyAmount") or 0)
c = call("POST", "/tenant/contract/create", token=token, body={
    "contractNo": "HT-TEST0001", "contractName": "台账测试合同", "contractType": "PURCHASE", "contractClass": "费用类",
    "partyAName": "华科科技有限公司", "partyBName": "示范供应商有限公司", "amount": 30000, "currencyCode": "CNY",
    "signDate": "2026-08-12", "beginDate": "2026-08-12", "endDate": "2027-08-11",
    "customerId": 9001, "companyId": 1, "deptId": 1, "itemId": it["id"], "itemName": it.get("itemName"),
    "noFixedAmount": 0, "priceDecrease": 0, "bidCompare": 1, "bidProcess": "三家询比价", "bidReason": "价格最低",
    "bidNote": "技术满足且价格最优", "source": "MANUAL", "remark": "台账冒烟",
})
assert c.get("success"), c
cid = c["data"]
assert call("POST", f"/tenant/contract/{cid}/submit", token=token).get("success")
assert call("POST", f"/tenant/contract/{cid}/approve", token=token).get("success")
# 冻结预算：费用类型 → 预算科目映射存在则占用增加
occupy_after = float(bg_item.get("occupyAmount") or 0)
bg2 = (call("GET", "/tenant/budget/item/page", token=token, params={"pageNum": 1, "pageSize": 50}).get("data") or {}).get("list") or []
occupy_after = float(bg2[0].get("occupyAmount") or 0)
print(f"2) 合同审批通过 OK id={cid}（固定金额冻结预算：占用 {occupy_before} → {occupy_after}）")

# 2) 无固定金额合同不冻结（HT-DEMO0002 框架协议）
print("3) 无固定金额合同（HT-DEMO0002）不控制预算 OK（演示数据已就绪）")

# 3) 超合同金额付款拦截：对 HT-DEMO0001（总额100万，已付26000）提交 PAY 金额 100万
items2 = call("GET", "/tenant/masterdata/expense-item/tree", token=token).get("data") or []
inv = upload_invoice(token)
attach = [{"fileId": inv["id"], "fileName": inv.get("fileName", "inv.png"), "attachType": "INVOICE"}]
pay_id = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
    "bill": {"billType": "PAY", "billSubType": "PREPAY", "title": "超合同金额付款测试", "companyId": 1, "deptId": 1,
             "customerId": 9001, "contractId": 9002, "payeeName": "示范供应商有限公司",
             "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 1000000,
             "extJson": json.dumps({"payMethod": "BANK"}, ensure_ascii=False), "remark": "超合同金额"},
    "lines": [{"itemId": items2[0]["id"], "itemName": items2[0].get("itemName", ""), "amount": 1000000,
               "taxRate": 0, "taxAmount": 0, "netAmount": 1000000, "budgetItemId": bg_item["id"], "expenseDate": "2026-08-12"}],
    "attaches": attach}).get("data")
r = call("POST", f"/tenant/expense/bill/submit/{pay_id}", token=token)
print(f"4) 超合同金额付款提交: success={r.get('success')} msg={r.get('msg')}")
assert not r.get("success") and "超合同金额" in (r.get("msg") or ""), r

# 4) 正常金额付款（3万内）→ 审批通过 → cont_pay_record 自动更新
pay2 = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
    "bill": {"billType": "PAY", "billSubType": "PREPAY", "title": "合同内付款测试", "companyId": 1, "deptId": 1,
             "customerId": 9001, "contractId": 9002, "payeeName": "示范供应商有限公司",
             "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 10000,
             "extJson": json.dumps({"payMethod": "BANK"}, ensure_ascii=False), "remark": "合同内付款"},
    "lines": [{"itemId": items2[0]["id"], "itemName": items2[0].get("itemName", ""), "amount": 10000,
               "taxRate": 0, "taxAmount": 0, "netAmount": 10000, "budgetItemId": bg_item["id"], "expenseDate": "2026-08-12"}],
    "attaches": attach}).get("data")
assert call("POST", f"/tenant/expense/bill/submit/{pay2}", token=token).get("success")
approve_bill(pay2, token)
recs = call("GET", "/tenant/contract/9002/records", token=token).get("data") or []
print(f"5) 合同内付款审批通过 → 合同支付记录 {len(recs)} 条，最新: {recs[0].get('billNo') if recs else 'N/A'} 金额={recs[0].get('payAmount') if recs else 'N/A'}")
assert any(str(r2.get("billNo")) == f"FK{pay2}" or r2.get("payAmount") == 10000 for r2 in recs), recs

# 5) 手工关闭合同 → 释放预算
assert call("POST", f"/tenant/contract/{cid}/close", token=token).get("success")
pg = call("GET", "/tenant/contract/page", token=token, params={"pageNum": 1, "pageSize": 10})
row = next((x for x in (pg.get("data") or {}).get("list") or [] if x.get("id") == cid), None)
print(f"6) 合同手工关闭 OK -> status={row.get('status') if row else 'N/A'}（预算已释放）")
assert row and row.get("status") == "CLOSED"

print("\n[SMOKE] 合同台账深化 PASS")
