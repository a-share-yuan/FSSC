# -*- coding: utf-8 -*-
"""组织合法性安全探针（三路体检修复后的反例回归）。
断言五件事：
  G1 跨公司建单被拒   —— 公司32用户 payload 传 companyId=31 → 403
  G2 同公司建单放行   —— 公司32用户 companyId=32 → 成功（守卫不误伤）
  G3 非办理人审批被拒 —— 公司32的部门经理审批 公司1 单据的节点 → 403（角色×公司操作鉴权）
  G4 读隔离           —— 公司1发票，公司32用户 page 看不到；alice(集团级)看得到
  G5 委托伪造被拒     —— fromUserId≠登录人 → "只能委托本人的待办"
自含种子+清理。运行：python scripts/smoke-security-guard.py（需 18080 在线）
"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:18080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
TS = str(int(time.time()))
PASS, FAIL = [], []


def sql(s):
    return subprocess.run([MYSQL, "-h192.168.20.77", "-uroot", "-p123456", "fssc",
                           "--default-character-set=utf8mb4", "-N", "-e", s],
                          capture_output=True, text=True, encoding="utf-8", errors="ignore").stdout.strip()


def q1(s):
    rows = [x for x in sql(s).split("\n") if x.strip()]
    return rows[-1].strip() if rows else ""


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        return json.loads(urllib.request.urlopen(req, timeout=30).read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"success": False, "msg": "HTTP %s" % e.code}


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(("  ✓ " if cond else "  ✗ ") + name + (("  | " + str(detail)[:80]) if detail else ""))


def login(u):
    r = call("POST", "/auth/login", {"username": u, "password": "123456",
                                     "tenantCode": "DEMO1", "subjectType": "TENANT"})
    return (r.get("data") or {}).get("token")


def kill_flow(proc):
    if not proc:
        return
    for t in ("ACT_RU_TASK", "ACT_RU_IDENTITYLINK", "ACT_RU_VARIABLE", "ACT_RU_JOB",
              "ACT_RU_TIMER_JOB", "ACT_RU_DEADLETTER_JOB", "ACT_RU_EVENT_SUBSCR"):
        sql("DELETE FROM %s WHERE PROC_INST_ID_='%s';" % (t, proc))
    sql("DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s' AND PARENT_ID_ IS NOT NULL AND ID_<>PROC_INST_ID_;" % proc)
    sql("DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s';" % proc)
    for t in ("ACT_HI_TASKINST", "ACT_HI_ACTINST", "ACT_HI_VARINST", "ACT_HI_IDENTITYLINK",
              "ACT_HI_COMMENT", "ACT_HI_PROCINST"):
        sql("DELETE FROM %s WHERE PROC_INST_ID_='%s';" % (t, proc))
    sql("DELETE FROM wf_task WHERE proc_inst_id='%s';" % proc)
    sql("DELETE FROM wf_instance WHERE proc_inst_id='%s';" % proc)


alice = login("alice")
assert alice, "alice 登录失败"
cid1 = int(q1("SELECT id FROM sys_company WHERE tenant_id=1 AND del_flag=0 ORDER BY id LIMIT 1;"))
dm = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='DEPT_MANAGER';")
ce = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='COMMON_EMP';")
fin = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='FIN';")
pw = q1("SELECT password FROM sys_user WHERE user_name='alice' AND tenant_id=1;")
u32 = None
bills, invs, procs = [], [], []

try:
    # 种子：公司32 部门经理（普通账号，非集团级）
    sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,status,is_admin,del_flag,create_time,update_time) "
        "VALUES (1,'SG_p32_%s','守卫探针32','SG_p32_%s','%s',32,'NORMAL',0,0,NOW(),NOW());" % (TS, TS, pw))
    u32 = q1("SELECT id FROM sys_user WHERE user_name='SG_p32_%s';" % TS)
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (u32, dm))
    if ce:
        sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (u32, ce))
    if fin:
        sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (u32, fin))
    t32 = login("SG_p32_" + TS)
    assert t32, "探针32登录失败（检查 user_name 列/密码哈希）"

    # G1/G2 建单公司合法性
    g1 = call("POST", "/tenant/expense/bill/saveDraft", token=t32, body={
        "bill": {"billType": "CLAIM", "title": "SG跨公司%s" % TS, "companyId": cid1,
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 330, "amount": 100.00, "taxRate": 0, "expenseDate": "2026-08-30"}], "attaches": []})
    check("G1 跨公司建单被拒", (not g1.get("success")) and ("组织合法性" in str(g1.get("msg")) or "无权" in str(g1.get("msg"))), g1.get("msg"))
    g2 = call("POST", "/tenant/expense/bill/saveDraft", token=t32, body={
        "bill": {"billType": "CLAIM", "title": "SG本公司%s" % TS, "companyId": 32,
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 330, "amount": 100.00, "taxRate": 0, "expenseDate": "2026-08-30"}], "attaches": []})
    check("G2 同公司建单放行", bool(g2.get("success")), g2.get("msg"))
    if g2.get("data"):
        bills.append(g2["data"])

    # G3 非办理人审批：alice 建公司1单并提交 → 公司32部门经理审批第一节点 → 403
    d = call("POST", "/tenant/expense/bill/saveDraft", token=alice, body={
        "bill": {"billType": "CLAIM", "title": "SG审批越权%s" % TS, "companyId": cid1,
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 330, "amount": 1000.00, "taxRate": 0, "expenseDate": "2026-08-30"}], "attaches": []})
    b = d.get("data")
    bills.append(b)
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % b)
    call("POST", "/tenant/expense/bill/submit/%s" % b, token=alice)
    proc = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % b)
    procs.append(proc)
    tid = q1("SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='%s' ORDER BY CREATE_TIME_ DESC LIMIT 1;" % proc)
    g3 = call("POST", "/tenant/wf/approve", token=t32, body={"taskId": tid, "approved": True, "comment": "越权试"})
    check("G3 非办理人/非本公司审批被拒", (not g3.get("success")) and ("无权" in str(g3.get("msg")) or "403" in str(g3.get("msg"))), g3.get("msg"))
    g3b = call("POST", "/tenant/wf/approve", token=alice, body={"taskId": tid, "approved": True, "comment": "合法"})
    check("G3+ 集团级账号审批正常(兼容)", bool(g3b.get("success")), g3b.get("msg"))

    # G4 读隔离：alice 开一张公司1发票 → 公司32用户 page 看不到
    no = "SGI" + TS
    call("POST", "/tenant/invoice/create", token=alice, body={
        "invoiceNo": no, "invoiceCode": "041001900111", "invoiceType": "VAT_SPECIAL",
        "sellerName": "守卫销方", "invoiceDate": "2026-08-30",
        "amount": 106.00, "taxAmount": 6.00, "netAmount": 100.00, "currencyCode": "CNY", "companyId": cid1})
    iid = q1("SELECT id FROM inv_invoice WHERE invoice_no='%s';" % no)
    if iid:
        invs.append((iid, no))
    page32 = json.dumps(call("GET", "/tenant/invoice/page?pageNum=1&pageSize=200", token=t32), ensure_ascii=False)
    pageA = json.dumps(call("GET", "/tenant/invoice/page?pageNum=1&pageSize=200", token=alice), ensure_ascii=False)
    check("G4-1 他公司发票读不到", no not in page32, "公司32 page 命中=泄漏")
    check("G4-2 集团级仍可见(兼容)", no in pageA, "alice page 未命中")

    # G5 委托伪造：公司32用户以 alice 名义建委托 → 拒
    g5 = call("POST", "/tenant/wf/config/delegate/create", token=t32, body={
        "fromUserId": 1, "toUserId": int(u32), "status": "NORMAL",
        "beginTime": "2026-08-31T00:00:00", "endTime": "2026-09-02T00:00:00"})
    check("G5 冒名委托被拒", (not g5.get("success")), "任一层拦截即达标: " + str(g5.get("msg"))[:40])
finally:
    for b in bills:
        if b:
            pi = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % b)
            kill_flow(pi)
            sql("DELETE FROM exp_bill_line WHERE bill_id=%s; DELETE FROM exp_bill_attach WHERE bill_id=%s; DELETE FROM exp_bill WHERE id=%s;" % (b, b, b))
    for p in procs:
        kill_flow(p)
    for iid, no in invs:
        sql("DELETE FROM inv_invoice WHERE id=%s; DELETE FROM inv_invoice_global WHERE invoice_no='%s';" % (iid, no))
    if u32:
        sql("DELETE FROM sys_user_role WHERE user_id=%s; DELETE FROM sys_user WHERE id=%s;" % (u32, u32))
    sql("DELETE FROM wf_delegate WHERE from_user_id=1 AND to_user_id=%s;" % (u32 or 0))
    print("[SEC-GUARD] 清理完成")

print("[SEC-GUARD] summary: pass=%d fail=%d" % (len(PASS), len(FAIL)))
if FAIL:
    print("  FAILED: " + ", ".join(FAIL))
sys.exit(1 if FAIL else 0)
