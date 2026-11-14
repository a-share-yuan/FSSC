# -*- coding: utf-8 -*-
"""多分子公司审批路由探针（V1.3.16）。
断言三件事：
  M-A 公司内唯一持有人 → 直线解析直接指派（company 31 单的第一节点 = probe31）
  M-B 跨公司不可见     → COMPANY 范围角色的候选组任务，别家公司同角色看不到
  M-C 共享段豁免       → GROUP 范围角色（共享会计）跨公司可见
测试账号/数据全部本脚本自造，finally 清理。
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
    print(("  ✓ " if cond else "  ✗ ") + name + (("  | " + str(detail)[:90]) if detail else ""))


def login(u):
    r = call("POST", "/auth/login", {"username": u, "password": "123456",
                                     "tenantCode": "DEMO1", "subjectType": "TENANT"})
    return (r.get("data") or {}).get("token")


def kill_flow(proc_inst):
    """SQL 清理一个 Flowable 实例（运行时+历史+业务镜像），探针专用"""
    if not proc_inst:
        return
    for t in ("ACT_RU_TASK", "ACT_RU_IDENTITYLINK", "ACT_RU_VARIABLE", "ACT_RU_JOB",
              "ACT_RU_TIMER_JOB", "ACT_RU_DEADLETTER_JOB", "ACT_RU_EVENT_SUBSCR"):
        sql("DELETE FROM %s WHERE PROC_INST_ID_='%s';" % (t, proc_inst))
    sql("DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s' AND PARENT_ID_ IS NOT NULL AND ID_<>PROC_INST_ID_;" % proc_inst)
    sql("DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s';" % proc_inst)
    for t in ("ACT_HI_TASKINST", "ACT_HI_ACTINST", "ACT_HI_VARINST", "ACT_HI_IDENTITYLINK",
              "ACT_HI_COMMENT", "ACT_HI_PROCINST"):
        sql("DELETE FROM %s WHERE PROC_INST_ID_='%s';" % (t, proc_inst))
    sql("DELETE FROM wf_task WHERE proc_inst_id='%s';" % proc_inst)
    sql("DELETE FROM wf_instance WHERE proc_inst_id='%s';" % proc_inst)


def kill_bill(bid):
    if not bid:
        return
    pi = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % bid)
    kill_flow(pi)
    sql("DELETE FROM exp_bill_line WHERE bill_id=%s; DELETE FROM exp_bill_attach WHERE bill_id=%s;"
        " DELETE FROM exp_bill WHERE id=%s;" % (bid, bid, bid))


alice = login("alice")
assert alice, "alice 登录失败"
cid1 = q1("SELECT id FROM sys_company WHERE tenant_id=1 AND del_flag=0 ORDER BY id LIMIT 1;")
dm_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='DIRECT_SUPERVISOR';")
fa_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='PRESIDENT';")
hash31 = q1("SELECT id FROM sys_user WHERE tenant_id=1 AND company_id=31 AND del_flag=0 ORDER BY id LIMIT 1;")
probe = {"p31": None, "p32": None, "p33": None, "p34": None, "p35": None}
bills = []
invoices = []

try:
    # ---- 种子：三个探针用户（密码同 alice），分属 31/32/33 公司 ----
    pw = q1("SELECT password FROM sys_user WHERE user_name='alice' AND tenant_id=1;")
    for name, comp, role, rid in (("MC_p31_" + TS, "31", "DIRECT_SUPERVISOR", dm_role),
                                  ("MC_p32_" + TS, "32", "DEPT_MANAGER", dm_role),
                                  ("MC_p33_" + TS, "33", "FSSC_AUDITOR", fa_role)):
        sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,status,is_admin,del_flag,create_time,update_time) "
            "VALUES (1,'%s','多公司探针%s','%s','%s',%s,'NORMAL',0,0,NOW(),NOW());" % (name, TS, name, pw, comp))
        uid = q1("SELECT id FROM sys_user WHERE user_name='%s';" % name)
        sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (uid, rid))
        if "p31" in name:
            probe["p31"] = uid
        elif "p32" in name:
            probe["p32"] = uid
        else:
            probe["p33"] = uid
    # 集团级经办（company NULL + DEMO_ADMIN 全权限）：OrgGuard 设计放行的跨公司代办路径
    # 归属公司31的探针部门（leader=p31）→ "直线上级"节点沿 sys_dept.leader_id 解析到 p31（applyResolution 指派）
    sql("INSERT INTO sys_dept(tenant_id,company_id,dept_code,dept_name,parent_id,leader_id,status,del_flag,create_time) "
        "VALUES (1,31,'MC%d','MC探针部门%d',0,%s,'NORMAL',0,NOW());" % (int(TS[-6:]), int(TS[-6:]), probe["p31"]))
    mc_dept = q1("SELECT id FROM sys_dept WHERE dept_code='MC%s' AND tenant_id=1;" % TS[-6:])
    da_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='DEMO_ADMIN';")
    sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,dept_id,status,is_admin,del_flag,create_time,update_time) "
        "VALUES (1,'MC_grp_%s','集团经办','MC_grp_%s','%s',NULL,%s,'NORMAL',0,0,NOW(),NOW());" % (TS, TS, pw, mc_dept))
    grp_uid = q1("SELECT id FROM sys_user WHERE user_name='MC_grp_%s';" % TS)
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (grp_uid, da_role))
    ce_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='COMMON_EMP';")
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (grp_uid, ce_role))
    # CLAIM 真实链首节点为"共享支持岗"(SHARING_SUPPORT, COMPANY范围)：单据公司31、alice(公司1)被隔离不可见
    # → 集团级 grp(公司NULL豁免)持该角色即可批首节点，推进到第二节点验证指派
    ss_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='SHARING_SUPPORT';")
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (grp_uid, ss_role))
    grp = login("MC_grp_" + TS)
    print("— 种子就绪：p31=%s p32=%s p33=%s grp=%s —" % (probe["p31"], probe["p32"], probe["p33"], grp_uid))

    # ---- M-A 公司内唯一持有人 → 直接指派 ----
    d = call("POST", "/tenant/expense/bill/saveDraft", token=grp, body={
        "bill": {"billType": "CLAIM", "title": "MC公司31单" + TS, "companyId": 31,
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 330, "amount": 1000.00, "taxRate": 0, "expenseDate": "2026-08-28"}], "attaches": []})
    b31 = d.get("data")
    bills.append(b31)
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % b31)
    s = call("POST", "/tenant/expense/bill/submit/%s" % b31, token=grp)
    check("M-A0 company31 提交成功", bool(s.get("success")), s.get("msg"))
    # CLAIM 真实设计链首节点为"共享支持岗"(SHARING_SUPPORT)，p31(公司31 部门经理)不持该角色
    # → 推进首节点后，"直线上级/部门经理"节点应按公司解析：p31 是公司31唯一 DEPT_MANAGER → 直接指派
    P31 = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % b31)
    for _ in range(3):
        node_now = q1("SELECT NAME_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='%s' ORDER BY CREATE_TIME_ DESC LIMIT 1;" % P31)
        if node_now and ("部门经理" in node_now or "直线上级" in node_now):
            break
        # submit 未传 approverUserId 时首节点兜底给租户管理员 alice——用 alice 推进（集团级全角色）
        todos_g = call("GET", "/tenant/wf/todo", token=grp).get("data") or []
        tg = next((x for x in todos_g if str(x.get("processInstanceId")) == str(P31)), None)
        if not tg:
            break
        rr = call("POST", "/tenant/wf/approve", token=grp, body={"taskId": tg["taskId"], "approved": True, "comment": "M-A推进"})
        if not rr.get("success"):
            break
    assignee = q1("SELECT IFNULL(ASSIGNEE_,'NULL') FROM ACT_RU_TASK WHERE PROC_INST_ID_='%s' ORDER BY CREATE_TIME_ DESC LIMIT 1;" % P31)
    check("M-A 公司内唯一持有人指派(部门经理节点)", assignee == str(probe["p31"]),
          "assignee=%s expect=%s" % (assignee, probe["p31"]))

    # ---- M-B 跨公司不可见（候选组 + COMPANY 范围）----
    d2 = call("POST", "/tenant/expense/bill/saveDraft", token=alice, body={
        "bill": {"billType": "CLAIM", "title": "MC公司1单" + TS, "companyId": int(cid1),
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 330, "amount": 1000.00, "taxRate": 0, "expenseDate": "2026-08-28"}], "attaches": []})
    b1 = d2.get("data")
    bills.append(b1)
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % b1)
    s2 = call("POST", "/tenant/expense/bill/submit/%s" % b1, token=alice)
    check("M-B0 company1 提交成功", bool(s2.get("success")), s2.get("msg"))
    proc1 = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % b1)
    t32 = login("MC_p32_" + TS)
    todo32 = json.dumps(call("GET", "/tenant/wf/todo", token=t32), ensure_ascii=False)
    todoA = json.dumps(call("GET", "/tenant/wf/todo", token=alice), ensure_ascii=False)
    check("M-B1 别家公司同角色看不到", (b1 and ("MC公司1单" + TS) not in todo32), "probe32 todo 命中公司1单=泄漏")
    check("M-B2 集团级账号仍可见(演示不破)", ("MC公司1单" + TS) in todoA, "alice todo 未见公司1单")

    # alice 推进业务节点，停在"总裁"节点（PRESIDENT 为 GROUP 范围，验证集团垂直角色跨公司可见）
    for _ in range(20):
        node_now = q1("SELECT NAME_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='%s' ORDER BY CREATE_TIME_ DESC LIMIT 1;" % proc1)
        if node_now and "总裁" in node_now:
            break
        todos = call("GET", "/tenant/wf/todo", token=alice).get("data") or []
        t = next((x for x in todos if str(x.get("processInstanceId")) == str(proc1)), None)
        if not t:
            break
        r = call("POST", "/tenant/wf/approve", token=alice,
                 body={"taskId": t["taskId"], "approved": True, "comment": "MC探针"})
        if not r.get("success"):
            break
    node = q1("SELECT NAME_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='%s' ORDER BY CREATE_TIME_ DESC LIMIT 1;" % proc1)
    t33 = login("MC_p33_" + TS)
    todo33 = json.dumps(call("GET", "/tenant/wf/todo", token=t33), ensure_ascii=False)
    check("M-C 集团垂直角色(GROUP)跨公司可见", ("MC公司1单" + TS) in todo33,
          "当前节点=%s probe33(公司33)可见性" % node)

    # ---- M-D/M-E 第4域补齐：发票开票流 INVOICE_ISSUE（rc/inv/po/md_inventory 同机制）----
    pa_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='FIN_AUDIT';")
    for key, name, comp in (("p34", "MC_p34_" + TS, "31"), ("p35", "MC_p35_" + TS, "32")):
        sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,status,is_admin,del_flag,create_time,update_time) "
            "VALUES (1,'%s','发票探针%s','%s','%s',%s,'NORMAL',0,0,NOW(),NOW());" % (name, TS, name, pw, comp))
        uid = q1("SELECT id FROM sys_user WHERE user_name='%s';" % name)
        sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s);" % (uid, pa_role))
        probe[key] = uid
    inv_no = "MCINV" + TS
    iss = call("POST", "/tenant/invoice/issue", token=grp, body={
        "invoiceNo": inv_no, "invoiceCode": "041001900111", "invoiceType": "VAT_SPECIAL",
        "sellerName": "MC探针销方", "buyerName": "MC探针购方", "invoiceDate": "2026-08-28",
        "amount": 1130.00, "taxAmount": 130.00, "netAmount": 1000.00, "currencyCode": "CNY", "companyId": 31})
    inv_id = iss.get("data")
    check("M-D0 开票(company31)成功", bool(iss.get("success")), iss.get("msg"))
    if inv_id:
        invoices.append(inv_id)
    inv_co = q1("SELECT company_id FROM inv_invoice WHERE id=%s;" % inv_id)
    check("M-D 写入路径 company_id=31", str(inv_co) == "31", "company_id=%s" % inv_co)
    proc_inv = q1("SELECT proc_inst_id FROM wf_instance WHERE biz_type='INVOICE_ISSUE' AND biz_id=%s ORDER BY id DESC LIMIT 1;" % inv_id)
    # 首节点"提交发票开具申请单"为发起人自办（assignee=grp）→ 先批掉，流转到第二节点"财务审核岗"(FIN_AUDIT)
    first_tid = q1("SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='%s' LIMIT 1;" % proc_inv)
    if first_tid:
        call("POST", "/tenant/wf/approve", token=grp, body={"taskId": first_tid, "approved": True, "comment": "MC首节点"})
    t34, t35 = login("MC_p34_" + TS), login("MC_p35_" + TS)
    hit34 = any(str(x.get("processInstanceId")) == str(proc_inv) for x in (call("GET", "/tenant/wf/todo", token=t34).get("data") or []))
    hit35 = any(str(x.get("processInstanceId")) == str(proc_inv) for x in (call("GET", "/tenant/wf/todo", token=t35).get("data") or []))
    check("M-E1 本公司财务审核岗可见开票单", hit34, "proc=%s" % proc_inv)
    check("M-E2 别家公司财务审核岗不可见", not hit35, "probe35(公司32)命中=泄漏")

    print("— 清理 —")
finally:
    for b in bills:
        try:
            kill_bill(b)
        except Exception as e:
            print("bill cleanup fail", b, e)
    for iv in invoices:
        try:
            pi = q1("SELECT proc_inst_id FROM wf_instance WHERE biz_type='INVOICE_ISSUE' AND biz_id=%s ORDER BY id DESC LIMIT 1;" % iv)
            no = q1("SELECT invoice_no FROM inv_invoice WHERE id=%s;" % iv)
            kill_flow(pi)
            sql("DELETE FROM inv_bill_invoice WHERE invoice_id=%s;" % iv)
            sql("DELETE FROM inv_invoice WHERE id=%s;" % iv)
            if no:
                sql("DELETE FROM inv_invoice_global WHERE invoice_no='%s';" % no)
        except Exception as e:
            print("invoice cleanup fail", iv, e)
    ids = ",".join([x for x in probe.values() if x])
    if ids:
        sql("DELETE FROM sys_user_role WHERE user_id IN (%s);" % ids)
        sql("DELETE FROM sys_user WHERE id IN (%s);" % ids)
    # grp 用户与探针部门清理
    if grp_uid:
        sql("DELETE FROM sys_user_role WHERE user_id=%s;" % grp_uid)
        sql("DELETE FROM sys_user WHERE id=%s;" % grp_uid)
    sql("DELETE FROM sys_dept WHERE dept_code LIKE 'MC%%' AND tenant_id=1 AND dept_name LIKE 'MC探针部门%%';")
    print("[MULTICOMPANY] summary: pass=%d fail=%d" % (len(PASS), len(FAIL)))
    if FAIL:
        print("  FAILED: " + ", ".join(FAIL))
    sys.exit(1 if FAIL else 0)
