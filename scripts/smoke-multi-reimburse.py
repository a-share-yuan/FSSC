# -*- coding: utf-8 -*-
# 分多次报销冒烟：APPLY 审批通过 → CLAIM1 关联 useAmount=60% → 审批 → 申请单 approved_amount 累计/占用核销
# → CLAIM2 关联剩余 → 审批 → 申请单 COMPLETED
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

def approve(bid, token):
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


token = call("POST", "/auth/login", {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"})["data"]["token"]
items = call("GET", "/tenant/masterdata/expense-item/tree", token=token).get("data") or []
it = items[0]
bg = (call("GET", "/tenant/budget/item/page", token=token, params={"pageNum": 1, "pageSize": 50}).get("data") or {}).get("list") or []
bg_item = bg[0] if bg else None
inv = upload_invoice(token)
attach = [{"fileId": inv["id"], "fileName": inv.get("fileName", "inv.png"), "attachType": "INVOICE"}]

# 1) 事前申请 6000，审批通过（占用 6000）
apply_id = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
    "bill": {"billType": "APPLY", "billSubType": "MEETING", "title": "分次报销-会议申请6000", "companyId": 1, "deptId": 1,
             "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 6000,
             "extJson": json.dumps({"meetingName": "分次报销测试"}, ensure_ascii=False), "remark": "分次报销"},
    "lines": [{"itemId": it["id"], "itemName": it.get("itemName", ""), "amount": 6000, "taxRate": 0, "taxAmount": 0, "netAmount": 6000,
               "budgetItemId": bg_item["id"] if bg_item else None, "expenseDate": "2026-08-12"}],
    "attaches": []}).get("data")
assert call("POST", f"/tenant/expense/bill/submit/{apply_id}", token=token).get("success")
approve(apply_id, token)
print(f"1) 事前申请审批通过 id={apply_id} 金额6000")

def new_claim(amt, title):
    return call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
        "bill": {"billType": "CLAIM", "billSubType": "TRAVEL", "title": title, "companyId": 1, "deptId": 1,
                 "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": amt,
                 "extJson": json.dumps({"reimburseType": "TRAVEL", "travelType": "DOMESTIC"}, ensure_ascii=False), "remark": "分次报销"},
        "lines": [{"itemId": it["id"], "itemName": it.get("itemName", ""), "amount": amt, "taxRate": 0, "taxAmount": 0, "netAmount": amt,
                   "budgetItemId": bg_item["id"] if bg_item else None, "expenseDate": "2026-08-12"}],
        "attaches": attach}).get("data")

# 2) CLAIM1 关联 3600（60%）→ 审批通过 → 申请单 approved_amount=3600
c1 = new_claim(3600, "分次报销-第1次3600")
assert call("POST", "/tenant/expense/detail/relation", token=token, params={"sourceBillId": apply_id, "targetBillId": c1, "relationType": "APPLY_CLAIM", "useAmount": 3600}).get("success")
assert call("POST", f"/tenant/expense/bill/submit/{c1}", token=token).get("success")
approve(c1, token)
det1 = call("GET", f"/tenant/expense/bill/detail/{apply_id}", token=token).get("data") or {}
a1 = det1.get("bill", {})
print(f"2) CLAIM1(3600) 审批通过 → 申请单 approvedAmount={a1.get('approvedAmount')} status={a1.get('status')}")
assert a1.get("approvedAmount") and abs(float(a1.get("approvedAmount")) - 3600) < 1 and a1.get("status") == "APPROVED"

# 3) CLAIM2 关联 2400（剩余）→ 审批通过 → 申请单 COMPLETED
c2 = new_claim(2400, "分次报销-第2次2400")
assert call("POST", "/tenant/expense/detail/relation", token=token, params={"sourceBillId": apply_id, "targetBillId": c2, "relationType": "APPLY_CLAIM", "useAmount": 2400}).get("success")
assert call("POST", f"/tenant/expense/bill/submit/{c2}", token=token).get("success")
approve(c2, token)
det2 = call("GET", f"/tenant/expense/bill/detail/{apply_id}", token=token).get("data") or {}
a2 = det2.get("bill", {})
print(f"3) CLAIM2(2400) 审批通过 → 申请单 approvedAmount={a2.get('approvedAmount')} status={a2.get('status')}")
assert a2.get("status") == "COMPLETED", a2
print("\n[SMOKE] 分多次报销语义 PASS（累计核销→COMPLETED）")
