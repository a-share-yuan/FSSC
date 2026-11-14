# -*- coding: utf-8 -*-
# 员工报销类冒烟：员工费用报销(CLAIM) 草稿→提交→审批→凭证自动生成；差旅培训报销(CLAIM/TRAVEL) 关联申请单
import json, urllib.request, urllib.error, urllib.parse, uuid, io, sys, os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import smoke_env

BASE = smoke_env.base_url()

def call(method, path, body=None, token=None, params=None, raw=None, ctype=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    if raw is not None:
        data = raw
    else:
        data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if ctype:
        req.add_header("Content-Type", ctype)
    else:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

def upload_invoice(token):
    # 1x1 像素 PNG
    png = bytes.fromhex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
        "0000000d49444154789c626001000000ffff03000006000557bfabd40000000049454e44ae426082")
    boundary = "----wb" + uuid.uuid4().hex
    parts = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"inv.png\"\r\nContent-Type: image/png\r\n\r\n".encode() + png + b"\r\n",
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"bizType\"\r\n\r\nINVOICE\r\n".encode(),
        f"--{boundary}--\r\n".encode(),
    ]
    body = b"".join(parts)
    r = call("POST", "/tenant/system/file/upload", token=token, raw=body, ctype=f"multipart/form-data; boundary={boundary}")
    assert r.get("success"), r
    return r["data"]  # 含 id/fileName

token = call("POST", "/auth/login", smoke_env.login_payload())["data"]["token"]
print("1) LOGIN OK")

# 菜单
mt = call("GET", "/tenant/system/menu/tree", token=token)
menus = mt.get("data") or []
def flat(n, out):
    out.append(n); [flat(c, out) for c in (n.get("children") or [])]
allm = []; [flat(m, allm) for m in menus]
names = {m.get("menuName"): m.get("path") for m in allm}
for expect in ["员工费用报销", "差旅培训报销"]:
    assert expect in names, f"菜单缺失: {expect}"
    print(f"  菜单 OK: {expect} -> {names[expect]}")

# 主数据
items = call("GET", "/tenant/masterdata/expense-item/tree", token=token).get("data") or []
bg = (call("GET", "/tenant/budget/item/page", token=token, params={"pageNum": 1, "pageSize": 50}).get("data") or {}).get("list") or []
it = items[0]; bg_item = bg[0] if bg else None
print(f"2) 主数据 OK: items={len(items)} budgets={len(bg)}")

# 上传占位发票（全部费用项 need_invoice=1，CLAIM 提交必须附发票）
inv = upload_invoice(token)
attach = [{"fileId": inv["id"], "fileName": inv.get("fileName", "inv.png"), "attachType": "INVOICE"}]
print(f"2.1) 发票上传 OK fileId={inv.get('id')}")

def new_claim(title, sub, extra_bill=None):
    bill = {"billType": "CLAIM", "billSubType": sub, "title": title, "companyId": 1, "deptId": 1,
            "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 0,
            "extJson": json.dumps({"payMethod": "BANK", "accountName": "爱丽丝", "paperAttachCount": 2}, ensure_ascii=False),
            "remark": "报销冒烟"}
    if extra_bill:
        bill.update(extra_bill)
    d = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
        "bill": bill,
        "lines": [{"itemId": it["id"], "itemName": it.get("itemName", ""), "amount": 1280.00,
                   "taxRate": 0, "taxAmount": 0, "netAmount": 1280.00,
                   "budgetItemId": bg_item["id"] if bg_item else None, "expenseDate": "2026-08-12"}],
        "attaches": attach,
    })
    assert d.get("success"), d
    return d["data"]

def approve_bill(bid):
    det = call("GET", f"/tenant/expense/bill/detail/{bid}", token=token)
    proc = (det.get("data") or {}).get("bill", {}).get("procInstId")
    # 多级审批流：逐级 complete 直到流程结束
    done = False
    for _ in range(30):
        todo = call("GET", "/tenant/wf/todo", token=token).get("data") or []
        t = next((x for x in todo if str(x.get("processInstanceId")) == str(proc)), None)
        if not t:
            done = True
            break
        r = call("POST", "/tenant/wf/approve", token=token, body={"taskId": t["taskId"], "approved": True, "comment": "同意"})
        assert r.get("success"), r
    assert done, f"多级审批未走完 proc={proc}"

# ============ A. 员工费用报销 ============
bid = new_claim("员工费用报销冒烟", "")
_r = call("POST", f"/tenant/expense/bill/submit/{bid}", token=token)
assert _r.get("success"), _r
pg = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "CLAIM"})
row = next((x for x in (pg.get("data") or {}).get("list") or [] if x.get("id") == bid), None)
assert row and row.get("status") == "APPROVING", row
print(f"3) 员工费用报销 提交 OK {row.get('billNo')} APPROVING")

approve_bill(bid)
pg2 = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "CLAIM"})
row2 = next((x for x in (pg2.get("data") or {}).get("list") or [] if x.get("id") == bid), None)
print(f"4) 员工费用报销 审批后状态={row2.get('status')} 凭证号={row2.get('voucherNo') or '无'}")
assert row2.get("status") in ("APPROVED", "POSTED"), row2

# ============ B. 差旅培训报销（先建已审批的会议申请，再关联） ============
apply_id = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
    "bill": {"billType": "APPLY", "billSubType": "MEETING", "title": "差旅报销关联申请（冒烟）", "companyId": 1, "deptId": 1,
             "applyDate": "2026-08-12", "periodYear": 2026, "currencyCode": "CNY", "totalAmount": 3000,
             "extJson": json.dumps({"meetingName": "出差申请", "startDate": "2026-08-20", "endDate": "2026-08-21"}, ensure_ascii=False),
             "remark": "用于关联"},
    "lines": [{"itemId": it["id"], "itemName": it.get("itemName", ""), "amount": 3000, "taxRate": 0, "taxAmount": 0, "netAmount": 3000,
               "budgetItemId": bg_item["id"] if bg_item else None, "expenseDate": "2026-08-12"}],
    "attaches": [],
}).get("data")
assert call("POST", f"/tenant/expense/bill/submit/{apply_id}", token=token).get("success")
approve_bill(apply_id)
print(f"5) 事前申请(关联用) 审批通过 id={apply_id}")

cbid = new_claim("差旅培训报销冒烟", "TRAVEL", {"extJson": json.dumps(
    {"reimburseType": "TRAVEL", "travelType": "DOMESTIC", "payMethod": "BANK", "accountName": "爱丽丝", "paperAttachCount": 1}, ensure_ascii=False)})
rel = call("POST", "/tenant/expense/detail/relation", token=token, params={"sourceBillId": apply_id, "targetBillId": cbid, "relationType": "APPLY_CLAIM", "useAmount": 3000})
assert rel.get("success"), rel
assert call("POST", f"/tenant/expense/bill/submit/{cbid}", token=token).get("success")
pg3 = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "CLAIM"})
row3 = next((x for x in (pg3.get("data") or {}).get("list") or [] if x.get("id") == cbid), None)
print(f"6) 差旅培训报销 关联申请单+提交 OK {row3.get('billNo')} {row3.get('status')}")
assert row3 and row3.get("status") == "APPROVING", row3

print("\n[SMOKE] 员工报销类(4.2) PASS")
