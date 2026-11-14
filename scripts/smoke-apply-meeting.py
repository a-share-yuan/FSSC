# -*- coding: utf-8 -*-
# 费用报销域深化 冒烟测试：菜单树 + 会议申请(APPLY/MEETING) 草稿→提交→审批
import json, urllib.request, urllib.error

BASE = "http://127.0.0.1:18080/api"

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
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

def login():
    r = call("POST", "/auth/login", {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"})
    assert r.get("success"), r
    return r["data"]["token"]

token = login()
print("1) LOGIN OK")

# 2) 菜单树
mt = call("GET", "/tenant/system/menu/tree", token=token)
menus = mt.get("data") or []
def flat(n, out):
    out.append(n); [flat(c, out) for c in (n.get("children") or [])]
allm = []; [flat(m, allm) for m in menus]
names = {m.get("menuName"): m.get("path") for m in allm}
for expect in ["会议申请", "出差申请", "培训申请", "还款管理", "事前申请", "借款管理"]:
    assert expect in names, f"菜单缺失: {expect}, 实际: {sorted(names.keys())}"
    print(f"  菜单 OK: {expect} -> {names[expect]}")

# 3) 主数据
items = call("GET", "/tenant/masterdata/expense-item/tree", token=token)
items = items.get("data") or []
meeting_item = next((x for x in items if "会议" in (x.get("itemName") or "")), items[0])
bg = call("GET", "/tenant/budget/item/page", token=token, params={"pageNum": 1, "pageSize": 50})
budgets = (bg.get("data") or {}).get("list") or []
bg_item = budgets[0] if budgets else None
print(f"2) 主数据 OK: 费用项={len(items)}, 预算科目={len(budgets)}")

# 4) 会议申请 草稿
draft = call("POST", "/tenant/expense/bill/saveDraft", token=token, body={
    "bill": {
        "billType": "APPLY", "billSubType": "MEETING",
        "title": "2026年8月财务共享例会（冒烟）",
        "companyId": 1, "deptId": 1, "applyDate": "2026-08-12", "periodYear": 2026,
        "currencyCode": "CNY", "totalAmount": 3500,
        "extJson": json.dumps({
            "meetingName": "财务共享月度例会", "meetingPlace": "烟台会议室A", "startDate": "2026-08-20",
            "endDate": "2026-08-20", "topic": "月度对账与付款计划", "attendees": 15, "note": "会议费",
        }, ensure_ascii=False),
        "remark": "会议申请冒烟测试",
    },
    "lines": [{"itemId": meeting_item["id"], "itemName": meeting_item["itemName"], "amount": 3500,
               "taxRate": 0, "taxAmount": 0, "netAmount": 3500,
               "budgetItemId": bg_item["id"] if bg_item else None, "expenseDate": "2026-08-20"}],
    "attaches": [],
})
assert draft.get("success"), draft
bill_id = draft["data"]
print(f"3) 会议申请草稿 OK id={bill_id}")

# 5) 提交
sub = call("POST", f"/tenant/expense/bill/submit/{bill_id}", token=token)
assert sub.get("success"), sub
print("4) 提交 OK -> 审批中")

# 6) 查询状态
pg = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "APPLY"})
rows = (pg.get("data") or {}).get("list") or []
me = next((x for x in rows if x.get("id") == bill_id), None)
assert me and me.get("status") == "APPROVING", f"状态异常: {me}"
print(f"5) 状态 APPROVING OK billNo={me.get('billNo')}")

# 7) 审批通过
det = call("GET", f"/tenant/expense/bill/detail/{bill_id}", token=token)
proc_inst = (det.get("data") or {}).get("bill", {}).get("procInstId")
todo = call("GET", "/tenant/wf/todo", token=token)
todo_list = todo.get("data") or []
task = next((t for t in todo_list if str(t.get("processInstanceId")) == str(proc_inst)), None)
assert task, f"未找到待办 procInst={proc_inst}: {[t.get('taskName') for t in todo_list]}"
ap = call("POST", "/tenant/wf/approve", token=token, body={"taskId": task.get("taskId"), "approved": True, "comment": "同意（冒烟）"})
assert ap.get("success"), ap
print("6) 审批通过 OK")

# 8) 终态
pg2 = call("GET", "/tenant/expense/bill/myPage", token=token, params={"pageNum": 1, "pageSize": 10, "billType": "APPLY"})
rows2 = (pg2.get("data") or {}).get("list") or []
me2 = next((x for x in rows2 if x.get("id") == bill_id), None)
print(f"7) 终态: {me2.get('status') if me2 else 'N/A'}")

print("\n[SMOKE] 会议申请全链路 PASS")
