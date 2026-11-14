# -*- coding: utf-8 -*-
"""V1.3.20 安全审计 5 项修复 · 运行时冒烟探针。

覆盖可在运行时直接观测的 4 项（① 4域 OrgGuard/IDOR、② deny_self fail-closed、
③ data_scope 五级真正生效、⑤ canOperate fail-closed）；第④项 multiType 废弃为
warn 日志（代码层已确认，无公开运行时入口，不在此脚本断言）。

设计：本机种子仅 DEMO_ADMIN 持有业务权限且为集团级（company_id=NULL，OrgGuard 放行），
故受限用户无法触达受 @RequiresPerm 守卫的接口。探针自造一个窄权限角色 SA_PROBE
（data_scope=2，授予 exp:bill:save/submit、sharing:voucher:list）+ 一家全新公司 99090
（仅 su 一名 DEPT_MANAGER，保证首节点单持有人直指派）+ 跨公司用户 p32，全部 finally 清理。

断言：
  ②  su 自造本公司单→首节点办理人=su→su 自审被拒（deny_self=1，fail-closed 默认开）
  ⑤  p32（公司32）越权代办 su 的公司99090 任务→被拒（canOperate fail-closed）
  ①  p32 读公司99090 凭证分录→FORBIDDEN（OrgGuard）；su/集团级 alice 放行
  ③  su（ds=2, 公司99090）凭证列表仅含本公司数据，不再泄漏公司1 的 105 张
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
    print(("  ✓ " if cond else "  ✗ ") + name + (("  | " + str(detail)[:110]) if detail else ""))


def login(u):
    r = call("POST", "/auth/login", {"username": u, "password": "123456",
                                     "tenantCode": "DEMO1", "subjectType": "TENANT"})
    return (r.get("data") or {}).get("token")


def kill_bill(bid):
    if not bid:
        return
    pi = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % bid)
    if pi:
        for t in ("ACT_RU_TASK", "ACT_RU_IDENTITYLINK", "ACT_RU_VARIABLE", "ACT_RU_JOB",
                  "ACT_RU_TIMER_JOB", "ACT_RU_DEADLETTER_JOB", "ACT_RU_EVENT_SUBSCR"):
            sql("DELETE FROM %s WHERE PROC_INST_ID_='%s';" % (t, pi))
        sql("DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s' AND PARENT_ID_ IS NOT NULL AND ID_<>PROC_INST_ID_;" % pi)
        sql("DELETE FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s';" % pi)
        for t in ("ACT_HI_TASKINST", "ACT_HI_ACTINST", "ACT_HI_VARINST", "ACT_HI_IDENTITYLINK",
                  "ACT_HI_COMMENT", "ACT_HI_PROCINST"):
            sql("DELETE FROM %s WHERE PROC_INST_ID_='%s';" % (t, pi))
        sql("DELETE FROM wf_task WHERE proc_inst_id='%s';" % pi)
        sql("DELETE FROM wf_instance WHERE proc_inst_id='%s';" % pi)
    sql("DELETE FROM exp_bill_line WHERE bill_id=%s; DELETE FROM exp_bill_attach WHERE bill_id=%s;"
        " DELETE FROM exp_bill WHERE id=%s;" % (bid, bid, bid))


alice = login("alice")
assert alice, "alice 登录失败"
pw = q1("SELECT password FROM sys_user WHERE user_name='alice' AND tenant_id=1;")
dm_role = q1("SELECT id FROM sys_role WHERE tenant_id=1 AND role_code='DEPT_MANAGER';")
subj = q1("SELECT id FROM md_subject WHERE tenant_id=1 AND status='NORMAL' LIMIT 1;")
bills = []
vid = None
menu_ids = {}  # 探针菜单 id（auto_increment，避免硬编码碰撞）


def add_menu(perms):
    mname = "SA探针_" + perms.replace(":", "_")
    # 注意：sys_menu 无 tenant_id 列，INSERT 不能带该字段（否则整条失败且被静默忽略）
    sql("INSERT INTO sys_menu(parent_id,menu_type,menu_name,perms,status,terminal) "
        "VALUES (0,'F','%s','%s',1,'PC');" % (mname, perms))
    return q1("SELECT id FROM sys_menu WHERE menu_name='%s' AND perms='%s' ORDER BY id DESC LIMIT 1;" % (mname, perms))


try:
    # ---- 种子：窄权限角色 + 探针菜单 + 全新公司 + 两名探针用户 + 一张本公司凭证 ----
    sql("INSERT INTO sys_role(id,tenant_id,role_code,role_name,data_scope) VALUES (99001,1,'SA_PROBE','SA安全审计探针',2);")
    for perms in ("exp:bill:save", "exp:bill:submit", "sharing:voucher:list"):
        menu_ids[perms] = add_menu(perms)
    sql("INSERT INTO sys_role_menu(tenant_id,role_id,menu_id) VALUES "
        "(1,99001,%s),(1,99001,%s),(1,99001,%s);" % (menu_ids["exp:bill:save"], menu_ids["exp:bill:submit"], menu_ids["sharing:voucher:list"]))
    sql("INSERT INTO sys_company(id,tenant_id,company_code,company_name,parent_id) VALUES (99090,1,'SA99090','SA安全审计探针公司',1);")
    sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,status,is_admin,del_flag,create_time,update_time) "
        "VALUES (1,'SA_su_%s','SA提交人','SA%s','%s',99090,'NORMAL',0,0,NOW(),NOW());" % (TS, TS, pw))
    su_id = q1("SELECT id FROM sys_user WHERE user_name='SA_su_%s';" % TS)
    sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,status,is_admin,del_flag,create_time,update_time) "
        "VALUES (1,'SA_p32_%s','SA跨公司','SA%s','%s',32,'NORMAL',0,0,NOW(),NOW());" % (TS, TS, pw))
    p32_id = q1("SELECT id FROM sys_user WHERE user_name='SA_p32_%s';" % TS)
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s),(1,%s,99001);" % (su_id, dm_role, su_id))
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,%s),(1,%s,99001);" % (p32_id, dm_role, p32_id))
    # 集团级探针：company_id=NULL（OrgGuard 设计内放行路径）
    sql("INSERT INTO sys_user(tenant_id,user_name,nick_name,emp_no,password,company_id,status,is_admin,del_flag,create_time,update_time) "
        "VALUES (1,'SA_grp_%s','SA集团级','SA%s','%s',NULL,'NORMAL',0,0,NOW(),NOW());" % (TS, TS, pw))
    grp_id = q1("SELECT id FROM sys_user WHERE user_name='SA_grp_%s';" % TS)
    sql("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES (1,%s,99001);" % grp_id)
    vno = "SAV" + TS
    sql("INSERT INTO op_voucher(tenant_id,voucher_no,company_id,period_year,period_month,voucher_date) VALUES (1,'%s',99090,2026,8,'2026-08-28');" % vno)
    vid = q1("SELECT id FROM op_voucher WHERE voucher_no='%s';" % vno)
    if subj:
        sql("INSERT INTO op_voucher_line(tenant_id,voucher_id,summary,subject_id,amount) VALUES (1,%s,'SA探针测试',%s,100.00);" % (vid, subj))
    su = login("SA_su_" + TS)
    p32 = login("SA_p32_" + TS)
    grp = login("SA_grp_" + TS)
    assert su and p32 and grp, "探针用户登录失败 su=%s p32=%s grp=%s" % (bool(su), bool(p32), bool(grp))
    # 探针用户 data_scope 应取最窄（min(DEPT_MANAGER=5, SA_PROBE=2)=2）
    ds_su = q1("SELECT data_scope FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id "
               "JOIN sys_role r ON r.id=ur.role_id WHERE u.id=%s ORDER BY r.data_scope ASC LIMIT 1;" % su_id)
    print("— 探针就绪 su=%s p32=%s vid=%s ds_su=%s —" % (su_id, p32_id, vid, ds_su))

    # ===== ② deny_self fail-closed：自造本公司单 + 首节点直指派给自己 + 自审必须被拒 =====
    d = call("POST", "/tenant/expense/bill/saveDraft", token=su, body={
        "bill": {"billType": "CLAIM", "title": "SA自审探针" + TS, "companyId": 99090,
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 330, "amount": 1000.00, "taxRate": 0, "expenseDate": "2026-08-28"}], "attaches": []})
    bsu = d.get("data")
    bills.append(bsu)
    # 报销单提交要求附发票（政策校验），探针补全一张 INVOICE 附件以满足提交前置
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % bsu)
    s = call("POST", "/tenant/expense/bill/submit/%s" % bsu, token=su)
    check("②-0 本公司单提交成功", bool(s.get("success")), s.get("msg"))
    assignee = q1("SELECT ASSIGNEE_ FROM ACT_RU_TASK WHERE PROC_INST_ID_=("
                  "SELECT proc_inst_id FROM exp_bill WHERE id=%s) ORDER BY CREATE_TIME_ ASC LIMIT 1;" % bsu)
    check("②-1 提交人即首节点办理人(单持有人直指派)", assignee == str(su_id), "assignee=%s expect=%s" % (assignee, su_id))
    pi_bsu = q1("SELECT proc_inst_id FROM exp_bill WHERE id=%s;" % bsu)
    t_su = next((t for t in (call("GET", "/tenant/wf/todo", token=su).get("data") or [])
                 if str(t.get("processInstanceId")) == pi_bsu), None)
    r_su = call("POST", "/tenant/wf/approve", token=su,
                body={"taskId": t_su["taskId"], "approved": True, "comment": "SA自审"})
    check("②-2 自审被拒(deny_self=1/fail-closed)",
          (not r_su.get("success")) and ("自审" in (r_su.get("msg") or "") or "禁止" in (r_su.get("msg") or "")),
          "msg=%s" % r_su.get("msg"))

    # ===== ⑤ canOperate fail-closed：p32（公司32）越权代办 su 的公司99090 任务 =====
    r_p32 = call("POST", "/tenant/wf/approve", token=p32,
                 body={"taskId": t_su["taskId"], "approved": True, "comment": "SA越权"})
    check("⑤ 跨公司越权代办被拒(canOperate fail-closed)",
          (not r_p32.get("success")) and ("办理人" in (r_p32.get("msg") or "") or "无权" in (r_p32.get("msg") or "")),
          "msg=%s" % r_p32.get("msg"))

    # ===== ① 4域 OrgGuard：凭证分录 IDOR =====
    r_p32lines = call("GET", "/tenant/sharing/voucher/%s/lines" % vid, token=p32)
    check("①-1 跨公司读取凭证分录被拒(OrgGuard FORBIDDEN)", not r_p32lines.get("success"), "msg=%s" % r_p32lines.get("msg"))
    r_sulines = call("GET", "/tenant/sharing/voucher/%s/lines" % vid, token=su)
    check("①-2 同公司读取凭证分录放行", bool(r_sulines.get("success")), "msg=%s" % r_sulines.get("msg"))
    r_alines = call("GET", "/tenant/sharing/voucher/%s/lines" % vid, token=grp)
    check("①-3 集团级(NULL公司)读取放行(设计内 bypass)", bool(r_alines.get("success")), "msg=%s" % r_alines.get("msg"))

    # ===== ③ data_scope 五级真正生效：su(ds=2,公司99090) 凭证列表仅含本公司 =====
    pg = call("GET", "/tenant/sharing/voucher/page?pageNum=1&pageSize=50", token=su)
    data = pg.get("data") or {}
    recs = data.get("records") or []
    tot = data.get("total") or 0
    co_set = sorted({str(r.get("companyId")) for r in recs})
    only_co = all(str(r.get("companyId")) == "99090" for r in recs) if recs else True
    check("③-1 凭证列表仅含本公司(ds=2 注入 company 条件)", only_co and tot == 1, "total=%s co_set=%s" % (tot, co_set))
    check("③-2 修复后不再泄漏他公司凭证(公司1 不出现)", "1" not in co_set, "leaked_co1=%s" % ("1" in co_set))

    print("— 清理 —")
finally:
    for b in bills:
        try:
            kill_bill(b)
        except Exception as e:
            print("bill cleanup fail", b, e)
    if vid:
        try:
            sql("DELETE FROM op_voucher_line WHERE voucher_id=%s;" % vid)
            sql("DELETE FROM op_voucher WHERE id=%s;" % vid)
        except Exception as e:
            print("voucher cleanup fail", e)
    ids = ",".join([x for x in (su_id, p32_id, grp_id) if x])
    if ids:
        sql("DELETE FROM sys_user_role WHERE user_id IN (%s);" % ids)
        sql("DELETE FROM sys_user WHERE id IN (%s);" % ids)
    sql("DELETE FROM sys_role_menu WHERE role_id=99001;")
    sql("DELETE FROM sys_role WHERE id=99001;")
    if menu_ids:
        sql("DELETE FROM sys_menu WHERE id IN (%s);" % ",".join(str(x) for x in menu_ids.values() if x))
    sql("DELETE FROM sys_company WHERE id=99090;")
    print("[SEC-AUDIT] summary: pass=%d fail=%d" % (len(PASS), len(FAIL)))
    if FAIL:
        print("  FAILED: " + ", ".join(FAIL))
    sys.exit(1 if FAIL else 0)
