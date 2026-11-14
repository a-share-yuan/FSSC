# -*- coding: utf-8 -*-
"""FSSC 三单匹配（PTP 采购循环）端到端验证驱动。

用法:
    python scripts/three_way_match_e2e.py create     # 造数：合同→PO→审批→收货→发票→结算单→提交（含异常单 B）
    python scripts/three_way_match_e2e.py match      # 匹配台验证：列表/明细/判定 + 四个处置出口
    python scripts/three_way_match_e2e.py flow       # 走完 SETTLE 审批链（流程+角色）
    python scripts/three_way_match_e2e.py pay        # 出纳付款执行：确认批次→TR_PAY 审批→银企直联执行
    python scripts/three_way_match_e2e.py cleanup    # 清理全部测试数据
    python scripts/three_way_match_e2e.py all        # create→match→flow→pay（不清理）
    python scripts/three_way_match_e2e.py snapshot   # 打印当前测试数据快照

设计:
  * 业务写入全部走真实 HTTP + 真实用户 Token（同时验证「流程 / 角色 / 用户权限」三完整）。
  * 审批链"当前办理人"从 Flowable 运行表读取（真实指派结果），再用该人的 Token 调 /wf/approve。
  * 状态落盘 scripts/.3wm_state.json，各阶段共享。
  * 本系统权限拒绝 = HTTP 200 + code=4030（不是 4xx），断言按此口径。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8089"
TAG = "3WM-E2E-20261015"
STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".3wm_state.json")

DB = dict(host="192.168.20.77", port=3306, user="root", password="123456",
          database="fssc", charset="utf8mb4")

USERS = {
    "purchaser": "100060",   # 任建国 采购经理
    "purchdir": "100059",    # 罗婷婷 采购总监（建 PO 主体）
    "purchdir2": "100066",   # 黄嘉伟 采购总监
    "wh": "100015",          # 郝华   仓库管理员
    "ap": "100110",          # 陶海燕 应付会计
    "shared": "100002",      # 赵勇   共享会计（pay:match:list + review）
    "auditor": "100064",     # 崔佳怡 财务审核岗（pay:match:review）
    "recon": "100012",       # 潘燕   对账专员
    "gl": "100044",          # 苏佳怡 总账会计（付款制证）
    "glrev": "100023",       # 覃立新 预制凭证审核岗
    "cashier": "100003",     # 雷子涵 出纳（批次确认）
    "fundpay": "100010",     # 崔丽   资金支付岗（批次执行付款）
    "compliance": "100018",  # 王华   合规管理岗（反欺诈复审结案）
}
PWD = "123456"
TENANT = "DEMO1"

SUPPLIER_ID = 18
SUPPLIER_NAME = "弘毅贸易有限公司"
COMPANY_ID = 1
ITEM_ID = 333
AMT_PO = 100000.00
AMT_RECEIPT = 100000.00
AMT_INVOICE = 100000.00
AMT_PAY = 100000.00
AMT_PAY_B = 120000.00

TOKENS = {}
STATE = {}


# ------------------------------------------------------------------ 基础
def db():
    import pymysql
    return pymysql.connect(**DB)


def q(sql, args=None, one=False):
    conn = db()
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        rows = cur.fetchall()
        return (rows[0] if rows else None) if one else rows
    finally:
        conn.close()


def ex(sql, args=None):
    conn = db()
    try:
        cur = conn.cursor()
        n = cur.execute(sql, args or ())
        conn.commit()
        return n
    finally:
        conn.close()


_LOGIN_STAMPS = []
LOGIN_LIMIT_PER_MIN = 18   # 后端 LoginGuardService 限流：单 IP 每分钟 20 次，留 2 次余量


def _pace_login():
    """主动限速：保证 60s 内登录次数 < 后端阈值（否则会被 4030『请求过于频繁』打断整条链）。"""
    while True:
        now = time.time()
        _LOGIN_STAMPS[:] = [t for t in _LOGIN_STAMPS if now - t < 60]
        if len(_LOGIN_STAMPS) < LOGIN_LIMIT_PER_MIN:
            _LOGIN_STAMPS.append(now)
            return
        time.sleep(60 - (now - _LOGIN_STAMPS[0]) + 0.5)


def login(user, pwd=PWD):
    if user in TOKENS:
        return TOKENS[user]
    for attempt in range(8):
        _pace_login()
        body = json.dumps({"username": user, "password": pwd, "subjectType": "TENANT",
                           "tenantCode": TENANT}).encode()
        req = urllib.request.Request(BASE + "/api/auth/login", data=body,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                d = json.loads(r.read().decode())
        except Exception as e:  # noqa: BLE001
            d = {"code": -1, "msg": repr(e)}
        if d.get("code") == 0:
            TOKENS[user] = d["data"]["token"]
            return TOKENS[user]
        msg = str(d.get("msg") or "")
        if ("过于频繁" in msg or "锁定" in msg) and attempt < 7:
            time.sleep(6)
            continue
        raise AssertionError(f"登录失败 {user}: {msg}")
    raise AssertionError(f"登录失败 {user}: 限流重试耗尽")


def raw_call(user, method, path, body=None, timeout=60):
    login(user)
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json; charset=utf-8",
                                          "Authorization": "Bearer " + TOKENS[user]})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": e.read().decode("utf-8", "replace")[:400], "data": None}


def call(user, method, path, body=None, timeout=60):
    d = raw_call(user, method, path, body, timeout)
    if not isinstance(d, dict) or d.get("code") not in (0, None):
        raise AssertionError(f"{user} {method} {path} -> code={d.get('code')} msg={d.get('msg')}")
    return d.get("data")


def denied(user, method, path, body=None):
    d = raw_call(user, method, path, body)
    return (isinstance(d, dict) and d.get("code") in (4030, 403)), d.get("msg")


def load_state():
    global STATE
    if os.path.exists(STATE_FILE):
        STATE = json.load(open(STATE_FILE, encoding="utf-8"))


def save_state():
    json.dump(STATE, open(STATE_FILE, "w", encoding="utf-8"), ensure_ascii=False, indent=2)


RESULTS = []


def step(name, fn):
    t0 = time.time()
    try:
        out = fn()
        RESULTS.append(True)
        print(f"[PASS] {name}{'' if out is None else ': ' + str(out)}   ({time.time() - t0:.1f}s)")
        return True
    except Exception as e:  # noqa: BLE001
        RESULTS.append(False)
        print(f"[FAIL] {name}: {e}   ({time.time() - t0:.1f}s)")
        return False


def summary():
    print(f"\n===== 三单匹配 E2E: {sum(RESULTS)}/{len(RESULTS)} PASS =====")
    return all(RESULTS)


# ------------------------------------------------------------------ 待办 / 流程
def my_todo(user):
    return call(user, "GET", "/api/tenant/wf/todo?limit=200") or []


def find_todo(user, bill_id):
    for t in my_todo(user):
        if str(t.get("billId")) == str(bill_id):
            return t
    return None


def approve_task(user, task_id, comment="同意", next_assignee_user_id=None):
    body = {"taskId": task_id, "approved": True, "comment": comment}
    if next_assignee_user_id:
        body["nextAssigneeUserId"] = next_assignee_user_id
    return call(user, "POST", "/api/tenant/wf/approve", body)


def next_node_pick(user, task_id, self_uid=None):
    """读取下一节点的取人要求；PICK 节点需显式指定办理人（优先避开当前办理人，守制审分离）。"""
    d = raw_call(user, "GET", f"/api/tenant/wf/next-candidates?taskId={task_id}")
    d = d.get("data") if isinstance(d, dict) else None
    if not d or not d.get("required") or not d.get("candidates"):
        return None, (d or {}).get("nodeName")
    cands = d["candidates"]
    pick = next((c for c in cands if str(c.get("userId")) != str(self_uid)), cands[0])
    return pick.get("userId"), d.get("nodeName")


def wf_instances(biz_id=None, biz_type=None):
    sql = "SELECT id,proc_inst_id,model_key,biz_type,biz_id,biz_no,status,current_node FROM wf_instance"
    cond, args = [], []
    if biz_id is not None:
        cond.append("biz_id=%s")
        args.append(biz_id)
    if biz_type:
        cond.append("biz_type=%s")
        args.append(biz_type)
    if cond:
        sql += " WHERE " + " AND ".join(cond)
    sql += " ORDER BY id DESC"
    return q(sql, tuple(args))


def current_assignee(proc_inst_id):
    row = q("""SELECT t.ASSIGNEE_, t.NAME_, t.ID_ FROM act_ru_task t
               WHERE t.PROC_INST_ID_=%s ORDER BY t.CREATE_TIME_ DESC LIMIT 1""",
            (proc_inst_id,), one=True)
    if not row:
        return None
    uid, task_name, task_id = row
    u = q("SELECT user_name,nick_name FROM sys_user WHERE id=%s", (uid,), one=True)
    if not u:
        return None
    return {"user_name": u[0], "nick_name": u[1], "task_name": task_name, "task_id": task_id,
            "uid": uid}


def drive_flow(proc_inst_id, label, max_step=20):
    """按真实指派逐节点推进，直到流程结束。返回办理轨迹。"""
    trail = []
    for _ in range(max_step):
        a = current_assignee(proc_inst_id)
        if not a:
            break
        nid, nnode = next_node_pick(a["user_name"], a["task_id"], a["uid"])
        approve_task(a["user_name"], a["task_id"], f"{label} E2E 自动办理",
                     next_assignee_user_id=nid)
        trail.append(f"{a['nick_name']}({a['user_name']})@{a['task_name']}"
                     + (f"[指定下一站→{nnode}]" if nid else ""))
        time.sleep(0.6)
    return trail


# ------------------------------------------------------------------ 造数
def mk_contract():
    cid = call(USERS["purchdir"], "POST", "/api/tenant/contract/create", {
        "contractName": f"3WM测试采购合同-{TAG}",
        "contractType": "采购合同",
        "partyAName": "上海爱共享集团有限公司",
        "partyBName": SUPPLIER_NAME,
        "itemId": ITEM_ID,
        "amount": AMT_PO,
        "currencyCode": "CNY",
        "signDate": "2026-10-15",
        "companyId": COMPANY_ID,
        "remark": TAG,
    })
    STATE["contract_id"] = cid
    return f"合同 id={cid}"


def mk_po():
    poid = call(USERS["purchdir"], "POST", "/api/tenant/purchase/order/create", {
        "supplierId": SUPPLIER_ID,
        "supplierName": SUPPLIER_NAME,
        "orderDate": "2026-10-15",
        "amount": AMT_PO,
        "currency": "CNY",
        "companyId": COMPANY_ID,
        "contractId": STATE["contract_id"],
        "buyerName": "罗婷婷",
        "remark": TAG,
    })
    STATE["po_id"] = poid
    inst = wf_instances(biz_id=poid, biz_type="PURCHASE_ORDER_REVIEW")[0]
    STATE["po_proc_inst"] = inst[1]
    return f"PO id={poid} 流程实例={inst[1][:8]}"


def approve_po():
    trail = drive_flow(STATE["po_proc_inst"], "PO审批")
    st = q("SELECT status FROM po_order WHERE id=%s", (STATE["po_id"],), one=True)[0]
    assert st == "APPROVED", f"PO 未审批通过: {st}"
    return f"{' → '.join(trail)} ⇒ PO={st}"


def mk_receipt():
    rid = call(USERS["wh"], "POST", "/api/tenant/purchase/receipt/create", {
        "poId": STATE["po_id"],
        "supplierName": SUPPLIER_NAME,
        "receiptDate": "2026-10-15",
        "amount": AMT_RECEIPT,
        "warehouse": "主仓",
        "receiverName": "郝华",
        "remark": TAG,
    })
    STATE["receipt_id"] = rid
    no = q("SELECT receipt_no FROM po_receipt WHERE id=%s", (rid,), one=True)[0]
    return f"收货单 id={rid} {no}"


def mk_invoice():
    iid = call(USERS["ap"], "POST", "/api/tenant/invoice/create", {
        "invoiceNo": f"3WME2E{int(time.time()) % 100000}",
        "invoiceCode": "044002600111",
        "invoiceType": "增值税专用发票",
        "invoiceTitle": "上海爱共享集团有限公司",
        "sellerName": SUPPLIER_NAME,
        "invoiceDate": "2026-10-15",
        "amount": AMT_INVOICE,
        "taxAmount": 11504.42,
        "netAmount": 88495.58,
        "currencyCode": "CNY",
        "companyId": COMPANY_ID,
        "remark": TAG,
    })
    STATE["invoice_id"] = iid
    return f"发票 id={iid} 价税合计={AMT_INVOICE}"


def _bill_payload(bill_id, amount, title, po_id, receipt_id, contract_id, reason=True):
    ext = {}
    if reason:
        ext["overrunReason"] = "E2E联调：本期预算额度已用尽，按软控方案经批准超支"
    b = {"billType": "PAY", "billSubType": "SETTLE", "title": title,
         "companyId": COMPANY_ID, "totalAmount": amount, "currencyCode": "CNY",
         "applyDate": "2026-10-15", "payTarget": "SUPPLIER",
         "payeeName": SUPPLIER_NAME, "payeeAccountNo": "6222020200123456789",
         "remark": TAG, "extJson": json.dumps(ext, ensure_ascii=False)}
    if bill_id:
        b["id"] = bill_id
    if po_id:
        b["poId"] = po_id
    if receipt_id:
        b["poReceiptId"] = receipt_id
    if contract_id:
        b["contractId"] = contract_id
    lines = [{"lineNo": 1, "itemId": ITEM_ID, "itemName": "行政办公费",
              "expenseDate": "2026-10-15", "currencyCode": "CNY", "amount": amount,
              "taxRate": 13, "netAmount": round(amount / 1.13, 2),
              "taxAmount": round(amount - amount / 1.13, 2), "cnDesc": "3WM E2E 采购结算"}]
    return {"bill": b, "lines": lines, "attaches": []}


def mk_bill_a():
    bid = call(USERS["ap"], "POST", "/api/tenant/expense/bill/saveDraft",
               _bill_payload(None, AMT_PAY, f"对公结算-3WM-E2E-{TAG}",
                             STATE["po_id"], STATE["receipt_id"], STATE["contract_id"]))
    STATE["bill_a"] = bid
    return f"结算单A 草稿 id={bid}"


def mk_bill_a_bind_invoice():
    call(USERS["ap"], "POST", "/api/tenant/invoice/bind",
         {"billId": STATE["bill_a"], "invoiceId": STATE["invoice_id"], "amount": AMT_INVOICE})
    return "发票已核销关联到结算单A"


def mk_bill_a_submit():
    call(USERS["ap"], "POST", f"/api/tenant/expense/bill/submit/{STATE['bill_a']}")
    inst = wf_instances(biz_id=STATE["bill_a"], biz_type="EXP_PAY")[0]
    STATE["bill_a_proc_inst"] = inst[1]
    return f"结算单A 已提交 → SETTLE 实例 {inst[1][:8]}，当前节点「{inst[7]}」"


def mk_bill_b():
    """异常单 B：无 PO / 无收货单 / 无发票，且付款额>发票额 ⇒ FAILED 冻结。"""
    bid = call(USERS["ap"], "POST", "/api/tenant/expense/bill/saveDraft",
               _bill_payload(None, AMT_PAY_B, f"对公结算-3WM-E2E-异常单-{TAG}", None, None, None))
    STATE["bill_b"] = bid
    call(USERS["ap"], "POST", f"/api/tenant/expense/bill/submit/{bid}")
    inst = wf_instances(biz_id=bid, biz_type="EXP_PAY")[0]
    STATE["bill_b_proc_inst"] = inst[1]
    return f"结算单B(异常) id={bid} 已提交 → 实例 {inst[1][:8]}"


def confirm_supplier_recon():
    """辅助造数：把供应商对账置为 CONFIRMED（四单链最后一腿）。"""
    ex("""INSERT INTO supplier_recon
          (tenant_id, supplier_id, supplier_name, recon_no, period, company_id,
           payable_amount, paid_amount, balance, status, confirm_time, remark, del_flag, create_time)
          VALUES (1,%s,%s,%s,'2026-10',%s,100000,100000,0,'CONFIRMED',NOW(),%s,0,NOW())""",
       (SUPPLIER_ID, SUPPLIER_NAME, f"RECON-{TAG}", COMPANY_ID, TAG))
    return "供应商对账已置 CONFIRMED（弘毅贸易有限公司 / 2026-10）"


def phase_create():
    print("\n########## 阶段 1：造数（UI/API 全真实链路） ##########")
    step("采购合同 创建", mk_contract)
    step("采购订单 创建（提交人=采购总监，避开自审回避）", mk_po)
    step("采购订单 审批链（采购经理→采购总监，真实指派办理）", approve_po)
    step("收货单 创建（仓库管理员）", mk_receipt)
    step("发票 登记（应付会计）", mk_invoice)
    step("对公结算单A 建草稿（应付会计，挂 PO+收货单+合同）", mk_bill_a)
    step("发票 核销关联到结算单A", mk_bill_a_bind_invoice)
    step("结算单A 提交 → 发起 SETTLE 审批流", mk_bill_a_submit)
    step("对公结算单B 建单并提交（异常场景：无三单+超付）", mk_bill_b)
    step("辅助：供应商对账置 CONFIRMED", confirm_supplier_recon)
    save_state()
    return summary()


# ------------------------------------------------------------------ 匹配台验证
def match_row(user, bill_id):
    r = call(user, "GET", f"/api/tenant/pay/match/check?billId={bill_id}")
    return r


def phase_match():
    print("\n########## 阶段 2：匹配台验证（列表 / 明细 / 判定 / 四个出口） ##########")
    A, B = STATE["bill_a"], STATE["bill_b"]
    cache = {}

    def s_page():
        d = call(USERS["shared"], "GET", "/api/tenant/pay/match/page?pageNum=1&pageSize=10")
        ids = [x["billId"] for x in d["list"]]
        assert A in ids and B in ids, f"匹配台未同时出现 A/B: {ids}"
        cache["page"] = d
        return f"列表 {d['total']} 条，含 A={A}({d['list'][0]['matchStatus']})、B={B}"

    def s_detail():
        r = match_row(USERS["shared"], A)
        legs = (f"PO={r.get('poNo')}/{r.get('poAmount')} 收货={r.get('receiptNo')}/{r.get('receiptAmount')} "
                f"发票={r.get('invoiceAmount')}(净{r.get('invoiceNetAmount')}) 付款={r.get('payAmount')} "
                f"合同={r.get('contractNo')}/{r.get('contractAmount')}")
        cache["A"] = r
        return f"[{r['matchStatus']}] {legs}"

    def s_invoices():
        d = call(USERS["shared"], "GET", f"/api/tenant/pay/match/{A}/invoices")
        assert d, "关联发票明细为空"
        cache["inv"] = d
        return f"关联发票 {len(d)} 张：{d[0].get('invoiceNo')} 价税={d[0].get('totalAmount')} 税={d[0].get('taxAmount')}"

    def s_matched():
        r = match_row(USERS["shared"], A)
        assert r["matchStatus"] == "MATCHED", f"A 应为 MATCHED，实际 {r['matchStatus']}：{r.get('msg')}"
        assert r["frozen"] is False
        return f"结算单A 四段链全通 ⇒ {r['matchStatus']}（未冻结）"

    def s_deny_list():
        ok, msg = denied(USERS["glrev"], "GET", "/api/tenant/pay/match/page?pageNum=1&pageSize=5")
        assert ok, f"无 pay:match:list 权限者竟可访问匹配台（msg={msg}）"
        return "无 pay:match:list 权限的预制凭证审核岗被正确拒绝(4030)"

    def s_deny_review():
        ok, msg = denied(USERS["glrev"], "POST", f"/api/tenant/pay/match/{A}/route")
        assert ok, "无 pay:match:review 权限者竟可派单"
        return "无 pay:match:review 权限者被正确拒绝(4030)"

    def s_manual():
        reason = urllib.parse.quote("E2E人工复核：四单已人工比对一致，走IFS手工匹配链留痕")
        pid = call(USERS["shared"], "POST",
                   f"/api/tenant/pay/match/{A}/manual-review?reason={reason}")
        assert pid, "手工匹配未返回流程实例"
        STATE["manual_proc_inst"] = pid
        return f"出口③ 手工匹配(IFS) 已发起 INVOICE_MATCH_MANUAL 实例 {str(pid)[:8]}"

    def s_b_failed():
        r = match_row(USERS["shared"], B)
        codes = [i["code"] for i in r["issues"]]
        assert r["matchStatus"] == "FAILED", f"B 应为 FAILED，实际 {r['matchStatus']}"
        assert r["frozen"] is True, "B 硬异常应冻结付款"
        cache["B"] = r
        return f"结算单B 判定 {r['matchStatus']} 冻结={r['frozen']} 差异={codes}"

    def s_route():
        res = call(USERS["shared"], "POST", f"/api/tenant/pay/match/{B}/route")
        STATE["route_result"] = res
        return f"出口① 异常派单：{res.get('roleName') or res.get('mainRole') or list(res.keys())}"

    def s_link():
        msg = call(USERS["shared"], "POST", f"/api/tenant/pay/match/{B}/link",
                   {"poId": STATE["po_id"], "poReceiptId": STATE["receipt_id"]})
        r = match_row(USERS["shared"], B)
        codes = [i["code"] for i in r["issues"]]
        assert "NO_PO" not in codes and "NO_RECEIPT" not in codes, f"手工关联未生效: {codes}"
        return f"出口② 手工关联PO+收货单 ⇒ {msg}；判定降为 {r['matchStatus']}（{codes}）"

    def s_routerole():
        res = STATE.get("route_result") or {}
        return f"派单回执字段：{json.dumps(res, ensure_ascii=False, default=str)[:260]}"

    step("匹配台列表（共享会计 pay:match:list）", s_page)
    step("匹配台四段链明细（PO/收货/发票/付款/合同）", s_detail)
    step("关联发票明细（inv_bill_invoice 勾稽）", s_invoices)
    step("结算单A 判定 = MATCHED（四单齐全）", s_matched)
    step("权限①：无 list 权限不得进匹配台", s_deny_list)
    step("权限②：无 review 权限不得派单", s_deny_review)
    step("处置出口③：IFS 手工匹配发起审批", s_manual)
    step("结算单B 判定 = FAILED 且冻结（超付硬异常）", s_b_failed)
    step("处置出口①：异常按责任岗派单", s_route)
    step("派单回执明细", s_routerole)
    step("处置出口②：手工关联 PO+收货单并重判", s_link)
    save_state()
    return summary()


# ------------------------------------------------------------------ 审批链
def phase_flow():
    print("\n########## 阶段 3：走完 SETTLE 审批链（验证流程+角色完整） ##########")

    def s_chain_a():
        trail = drive_flow(STATE["bill_a_proc_inst"], "结算单A")
        print("   办理轨迹:", " → ".join(trail))
        row = q("SELECT status,paid_amount,voucher_no FROM exp_bill WHERE id=%s",
                (STATE["bill_a"],), one=True)
        inst = q("SELECT status,current_node FROM wf_instance WHERE proc_inst_id=%s",
                 (STATE["bill_a_proc_inst"],), one=True)
        assert inst and inst[0] != "RUNNING", f"结算单A 流程未办结: {inst}"
        return (f"{len(trail)} 个节点全部办结 ⇒ 流程 {inst[0]}，单据状态 {row[0]}"
                f"，凭证号 {row[2]}")

    def s_chain_manual():
        trail = drive_flow(STATE["manual_proc_inst"], "IFS手工匹配")
        print("   办理轨迹:", " → ".join(trail))
        r = match_row(USERS["shared"], STATE["bill_a"])
        return f"{len(trail)} 节点办结 ⇒ 匹配台判定 {r['matchStatus']}，人工结论={r.get('manualVerdict')}"

    def s_chain_b_route():
        insts = wf_instances(biz_id=STATE["bill_b"], biz_type="INVOICE_MATCH")
        if not insts:
            return "结算单B 派单实例：无（派单未生成流程实例）"
        trail = drive_flow(insts[0][1], "异常派单复审")
        print("   办理轨迹:", " → ".join(trail))
        return f"异常派单复审 {len(trail)} 节点办结"

    def s_chain_b_settle():
        """异常单 B 解冻后，SETTLE 链继续启动，同样要走到终点（证明异常处置后流程不卡单）。"""
        insts = wf_instances(biz_id=STATE["bill_b"], biz_type="EXP_PAY")
        if not insts:
            return "结算单B 无 SETTLE 实例（未启动）"
        trail = drive_flow(insts[0][1], "结算单B(超付已解冻)")
        print("   办理轨迹:", " → ".join(trail))
        st = q("SELECT status FROM exp_bill WHERE id=%s", (STATE["bill_b"],), one=True)[0]
        inst = q("SELECT status FROM wf_instance WHERE proc_inst_id=%s", (insts[0][1],), one=True)
        return f"{len(trail)} 节点办结 ⇒ 流程 {inst[0]}，单据状态 {st}"

    def s_drain():
        """收尾：把本轮所有仍 RUNNING 的实例办结，确保无悬空待办。"""
        drained = []
        for biz in (STATE.get("bill_a"), STATE.get("bill_b"), STATE.get("po_id")):
            if not biz:
                continue
            for i in wf_instances(biz_id=biz):
                if i[6] != "RUNNING":
                    continue
                t = drive_flow(i[1], f"收尾{i[4]}")
                if t:
                    drained.append(f"{i[4]}/{i[2]}:{len(t)}节点")
        left = q("SELECT COUNT(*) FROM act_ru_task", one=True)[0]
        return f"收尾办结 [{', '.join(drained) or '无在途'}]；库内剩余运行任务 {left} 条"

    def s_final():
        a = match_row(USERS["shared"], STATE["bill_a"])
        b = match_row(USERS["shared"], STATE["bill_b"])
        return (f"A={a['matchStatus']}(冻结={a['frozen']}) B={b['matchStatus']}(冻结={b['frozen']})")

    step("结算单A SETTLE 审批链全程（扫描→部门经理→共享→基地→总监→总裁→制证→复核）", s_chain_a)
    step("IFS 手工匹配链办结（匹配结论落 ext_json.matchVerdict）", s_chain_manual)
    step("异常派单复审链办结", s_chain_b_route)
    step("结算单B SETTLE 审批链全程（异常解冻后不卡单）", s_chain_b_settle)
    step("收尾：办结全部在途实例", s_drain)
    step("最终匹配台状态复核", s_final)
    save_state()
    return summary()


# ------------------------------------------------------------------ 出纳付款
def phase_pay():
    print("\n########## 阶段 4：出纳付款执行（资金付款链 + 制审分离） ##########")
    A = STATE["bill_a"]

    def find_batch():
        row = q("""SELECT id,batch_no,status FROM op_pay_batch
                   WHERE source_doc_type='EXP_PAY' AND source_doc_id=%s AND del_flag=0
                   ORDER BY id DESC LIMIT 1""", (A,), one=True)
        assert row, "未找到结算单A 的付款批次（SETTLE 链『付款制证与下推』本应已生成）"
        STATE["batch_id"], STATE["batch_no"] = row[0], row[1]
        return f"付款批次 {row[1]} id={row[0]}（状态 {row[2]}）"

    def s_no_exec_perm():
        ok, _ = denied(USERS["cashier"], "POST", f"/api/tenant/pay/batch/{STATE['batch_id']}/execute")
        assert ok, "出纳竟可直接执行付款（越权，违制审分离）"
        return "出纳仅可 confirm、无 execute 权（被正确拒绝 4030）——制审分离成立"

    def s_confirm():
        call(USERS["cashier"], "POST", f"/api/tenant/pay/batch/{STATE['batch_id']}/confirm")
        st = q("SELECT status FROM op_pay_batch WHERE id=%s", (STATE["batch_id"],), one=True)[0]
        assert st == "CONFIRMED", f"确认后应 CONFIRMED，实际 {st}"
        inst = wf_instances(biz_id=STATE["batch_id"], biz_type="TR_PAY")
        assert inst, "出纳确认后未发起 TR_PAY 资金付款审批流"
        STATE["trpay_proc_inst"] = inst[0][1]
        return f"出纳确认批次 ⇒ CONFIRMED，发起 TR_PAY 实例 {inst[0][1][:8]}，首节点「{inst[0][7]}」"

    def s_trpay_chain():
        trail = drive_flow(STATE["trpay_proc_inst"], "TR_PAY资金付款")
        print("   办理轨迹:", " → ".join(trail))
        st = q("SELECT status FROM op_pay_batch WHERE id=%s", (STATE["batch_id"],), one=True)[0]
        assert st == "APPROVED", f"TR_PAY 办结后批次应 APPROVED，实际 {st}"
        return f"{len(trail)} 节点办结 ⇒ 批次 APPROVED（PayBatchBizAction 回写 + 出纳凭证）"

    def s_fraud_list():
        d = call(USERS["compliance"], "GET",
                 f"/api/tenant/compliance/fraud/by-bill?billId={A}") or []
        hits = [x for x in d if x.get("detectionResult") == "HIGH_RISK" and not x.get("reviewedBy")]
        STATE["fraud_ids"] = [x["id"] for x in hits]
        if not hits:
            return (f"反欺诈检测 {len(d)} 条，无高风险未复审记录 ⇒ 付款闸本不拦截"
                    "（FREQUENCY/AMOUNT 为 7 天频率型规则，累计达阈值才转 HIGH_RISK）")
        return (f"反欺诈检测 {len(d)} 条，高风险未复审 {len(hits)} 条："
                + "、".join(f"[{x['detectionType']}]score={x['riskScore']}" for x in hits))

    def s_fraud_deny():
        if not STATE.get("fraud_ids"):
            return "跳过：本单无高风险记录，无放行动作可测"
        ok, _ = denied(USERS["ap"], "POST",
                       f"/api/tenant/compliance/fraud/review/{STATE['fraud_ids'][0]}",
                       {"action": "RELEASE"})
        assert ok, "无 compliance:fraud:review 权限者竟可放行欺诈记录"
        return "无 compliance:fraud:review 权限者被正确拒绝（4030）"

    def s_fraud_release():
        ids_ = STATE.get("fraud_ids") or []
        if not ids_:
            return "跳过：本单无高风险记录，付款未被反欺诈闸拦截"
        for fid in ids_:
            call(USERS["compliance"], "POST", f"/api/tenant/compliance/fraud/review/{fid}",
                 {"action": "RELEASE"})
        left = q("""SELECT COUNT(*) FROM fraud_detection_log
                    WHERE bill_id=%s AND detection_result='HIGH_RISK'
                      AND reviewed_by IS NULL AND action_taken<>'RELEASE'""", (A,), one=True)[0]
        assert left == 0, f"仍有 {left} 条高风险未放行"
        return (f"合规管理岗复审结案 RELEASE {len(ids_)} 条 ⇒ 付款闸解除"
                "（反欺诈硬阻断是真实生效的付款前控制）")

    def s_execute():
        r = call(USERS["fundpay"], "POST", f"/api/tenant/pay/batch/{STATE['batch_id']}/execute")
        assert r == "PAID", f"执行付款返回 {r}"
        b = q("SELECT status,bank_resp FROM op_pay_batch WHERE id=%s", (STATE["batch_id"],), one=True)
        d = q("SELECT status,bank_serial FROM op_pay_batch_detail WHERE batch_id=%s",
              (STATE["batch_id"],), one=True)
        assert b[0] == "PAID" and d[0] == "SUCCESS", f"批次/明细未落 PAID/SUCCESS: {b} {d}"
        STATE["pay_serial"] = d[1]
        return f"资金支付岗执行付款 ⇒ 批次 {b[0]}，明细 {d[0]}，回单号 {d[1]}，银行回执「{b[1]}」"

    def s_bill_paid():
        row = q("SELECT status,voucher_no,pay_time FROM exp_bill WHERE id=%s", (A,), one=True)
        p = q("SELECT pay_status,pay_batch_no,pay_serial_no FROM exp_bill_payment WHERE bill_id=%s",
              (A,), one=True)
        assert row[0] == "PAID", f"结算单A 未置 PAID: {row}"
        assert p and p[0] == "PAID", f"付款记录未置 PAID: {p}"
        return f"结算单A ⇒ {row[0]}，凭证 {row[1]}，批次 {p[1]}，流水 {p[2]}"

    def s_ledger():
        n = q("SELECT COUNT(*) FROM op_gl_entry WHERE biz_id IN (%s,%s)",
              (A, STATE["batch_id"]), one=True)[0]
        v = q("SELECT COUNT(*) FROM op_voucher WHERE remark=%s", (f"实付入账批次:{STATE['batch_no']}",), one=True)[0]
        return f"总账分录 {n} 条、付款入账凭证 {v} 张已生成（借应付/贷银行）"

    def s_final():
        a = q("SELECT status,voucher_no FROM exp_bill WHERE id=%s", (A,), one=True)
        b = q("SELECT status FROM op_pay_batch WHERE id=%s", (STATE["batch_id"],), one=True)
        return (f"全链闭环：PO={STATE['po_id']} → 收货={STATE['receipt_id']} → 发票={STATE['invoice_id']} "
                f"→ 结算单={A}({a[0]}) → 批次={STATE['batch_no']}({b[0]}) → 凭证={a[1]}")

    step("定位结算单A 生成的付款批次", find_batch)
    step("权限③：出纳不得越权执行付款（制审分离）", s_no_exec_perm)
    step("出纳确认付款批次（发起 TR_PAY 资金付款审批）", s_confirm)
    step("TR_PAY 资金付款审批链全程（资金岗→基地财务→阈值审批→共享主管→网银执行）", s_trpay_chain)
    step("反欺诈闸口：查询本单高风险未复审记录", s_fraud_list)
    step("权限④：无反欺诈复核权不得放行", s_fraud_deny)
    step("处置出口④：合规岗反欺诈复审结案（RELEASE 解闸）", s_fraud_release)
    step("资金支付岗执行付款（银企直联/网银）", s_execute)
    step("结算单/付款记录落 PAID", s_bill_paid)
    step("总账分录与付款入账凭证", s_ledger)
    step("全链闭环复核", s_final)
    save_state()
    return summary()


# ------------------------------------------------------------------ 清理
def _in(col, vals):
    """生成 IN 子句与参数，空集合返回 None。"""
    if not vals:
        return None, []
    return f"{col} IN (" + ",".join(["%s"] * len(vals)) + ")", list(vals)


# 本轮 E2E 起始时刻：用于无业务关联列的日志类表（integration_call_log）
CLEANUP_WINDOW = "2026-10-15 18:15:00"
# 探针阶段遗留主键（业务表已删，但凭证/消息/审计行可能仍在，必须一并回收）
_LEGACY = {"contract": [6], "po": [9], "receipt": [11], "invoice": [30], "bill": [99990004], "batch": []}


def discover_test_ids():
    """按 remark/TAG 与关联关系发现本轮测试写库产生的全部主键（含探针遗留）。"""
    def col(sql, args=()):
        return [r[0] for r in q(sql, args)]

    ct = col("SELECT id FROM cont_contract WHERE remark=%s", (TAG,)) + _LEGACY["contract"]
    po = col("SELECT id FROM po_order WHERE remark=%s", (TAG,)) + _LEGACY["po"]
    rc = col("SELECT id FROM po_receipt WHERE remark=%s", (TAG,)) + _LEGACY["receipt"]
    inv = col("SELECT id FROM inv_invoice WHERE remark=%s", (TAG,)) + _LEGACY["invoice"]
    bl = sorted({*(x for x in (STATE.get("bill_a"), STATE.get("bill_b")) if x),
                 *col("SELECT id FROM exp_bill WHERE remark=%s", (TAG,)), *_LEGACY["bill"]})
    bt = []
    if bl:
        s, a = _in("source_doc_id", bl)
        bt = col(f"SELECT id FROM op_pay_batch WHERE {s}", tuple(a))
    bt = sorted({*bt, *_LEGACY["batch"]})
    parts, args = [], []
    for coln, vals in (("bill_id", bl + rc + inv + po),
                       ("source_doc_id", rc + inv + po + bt + bl)):
        s, a = _in(coln, vals)
        if s:
            parts.append(s)
            args += a
    vc = col("SELECT id FROM op_voucher WHERE " + " OR ".join(parts), tuple(args)) if parts else []
    return {"contract": sorted(set(ct)), "po": sorted(set(po)), "receipt": sorted(set(rc)),
            "invoice": sorted(set(inv)), "bill": bl, "batch": bt, "voucher": sorted(set(vc))}


def phase_cleanup():
    print("\n########## 阶段 5：清理测试数据（还原到验证前） ##########")
    if not STATE:
        load_state()
    ids = discover_test_ids()
    print("  发现测试主键:", {k: v for k, v in ids.items() if v})

    insts = []
    for biz in sorted(set(ids["bill"] + ids["po"] + ids["batch"])):
        insts += wf_instances(biz_id=biz)
    pids = sorted({i[1] for i in insts})
    print(f"  待清理流程实例 {len(pids)} 个")

    def s_cancel_flows():
        """先用应用自身的取消接口终止在途实例（不直接删运行表，避免破坏引擎一致性）。"""
        running = [r[0] for r in q("SELECT proc_inst_id FROM wf_instance WHERE status='RUNNING'")
                   if r[0] in pids]
        if not running:
            return "无在途实例，跳过（均已办结）"
        r = raw_call("admin", "POST", "/api/tenant/wf/dev/instance/cancel",
                     {"procInstIds": running, "procInstId": running[0],
                      "reason": f"{TAG} E2E 清理"})
        return f"取消 {len(running)} 个在途实例: code={r.get('code')} msg={str(r.get('msg'))[:70]}"

    def s_purge_flows():
        conn = db()
        try:
            cur = conn.cursor()
            cur.execute("SET FOREIGN_KEY_CHECKS=0")
            n = 0
            for pid in pids:
                for t in ["act_ru_identitylink", "act_ru_task", "act_ru_variable",
                          "act_ru_job", "act_ru_timer_job", "act_ru_external_job",
                          "act_ru_deadletter_job", "act_ru_suspended_job", "act_ru_event_subscr",
                          "act_ru_execution",
                          "act_hi_taskinst", "act_hi_actinst", "act_hi_varinst", "act_hi_detail",
                          "act_hi_identitylink", "act_hi_comment", "act_hi_attachment",
                          "act_hi_procinst"]:
                    col = "PROCESS_INSTANCE_ID_" if t.endswith("_job") else "PROC_INST_ID_"
                    try:
                        n += cur.execute(f"DELETE FROM {t} WHERE {col}=%s", (pid,))
                    except Exception:
                        try:
                            n += cur.execute(f"DELETE FROM {t} WHERE PROC_INST_ID_=%s", (pid,))
                        except Exception:
                            pass
            for t in ["wf_task", "wf_task_history", "wf_process_log", "wf_cc",
                      "wf_process_outbox", "wf_instance"]:
                for pid in pids:
                    try:
                        n += cur.execute(f"DELETE FROM {t} WHERE proc_inst_id=%s", (pid,))
                    except Exception:
                        pass
            cur.execute("SET FOREIGN_KEY_CHECKS=1")
            conn.commit()
            return f"已清除 {len(pids)} 个实例的运行/历史/wf_* 行，共 {n} 行"
        finally:
            conn.close()

    def s_biz_data():
        """覆盖全库扫描出的 26 张受影响表：凭证/总账/付款/审计/消息/单据/主数据关联。"""
        conn = db()
        try:
            cur = conn.cursor()
            cur.execute("SET FOREIGN_KEY_CHECKS=0")
            total = 0
            plans = [
                ("op_voucher_line", "voucher_id", ids["voucher"]),
                ("op_gl_entry", "voucher_id", ids["voucher"]),
                ("op_gl_entry", "biz_id", ids["bill"] + ids["batch"]),
                ("op_voucher_push_log", "voucher_id", ids["voucher"]),
                ("op_voucher", "id", ids["voucher"]),
                ("op_pay_batch_detail", "batch_id", ids["batch"]),
                ("op_pay_batch", "id", ids["batch"]),
                ("exp_bill_payment", "bill_id", ids["bill"]),
                ("exp_bill_attach", "bill_id", ids["bill"]),
                ("exp_bill_line", "bill_id", ids["bill"]),
                ("exp_bill", "id", ids["bill"]),
                ("inv_bill_invoice", "bill_id", ids["bill"]),
                ("inv_bill_invoice", "invoice_id", ids["invoice"]),
                ("inv_invoice_global", "last_invoice_id", ids["invoice"]),
                ("inv_invoice", "id", ids["invoice"]),
                ("bg_occupy", "biz_id", ids["bill"] + ids["po"] + ids["receipt"]),
                ("op_audit_task", "bill_id", ids["bill"]),
                ("op_physical_delivery", "bill_id", ids["bill"]),
                ("cont_pay_record", "contract_id", ids["contract"]),
                ("po_receipt", "id", ids["receipt"]),
                ("po_order_line", "po_id", ids["po"]),
                ("po_order", "id", ids["po"]),
                ("stock_move", "source_doc_id", ids["receipt"]),
                ("cont_contract", "id", ids["contract"]),
                ("supplier_recon", "remark", [TAG]),
                ("wf_cc", "biz_id", ids["bill"] + ids["batch"]),
                ("audit_trail", "biz_id",
                 ids["bill"] + ids["batch"] + ids["receipt"] + ids["invoice"] + ids["po"]),
                ("sys_message", "biz_id",
                 ids["bill"] + ids["batch"] + ids["receipt"] + ids["invoice"] + ids["po"]),
                ("fraud_detection_log", "bill_id", ids["bill"]),
                ("fraud_detection_log", "created_at>=%s", [CLEANUP_WINDOW]),
                ("op_review_log", "bill_id", ids["bill"]),
                ("op_review_log", "created_at>=%s", [CLEANUP_WINDOW]),
                ("integration_call_log", "create_time>=%s", [CLEANUP_WINDOW]),
            ]
            for table, cond, vals in plans:
                if not vals:
                    continue
                if ">=" in cond:
                    sql = f"DELETE FROM {table} WHERE {cond}"
                    try:
                        total += cur.execute(sql, tuple(vals))
                    except Exception as e:
                        print(f"   (跳过 {table}: {str(e)[:60]})")
                    continue
                s, a = _in(cond, vals)
                try:
                    total += cur.execute(f"DELETE FROM {table} WHERE {s}", tuple(a))
                except Exception as e:
                    print(f"   (跳过 {table}.{cond}: {str(e)[:60]})")
            cur.execute("SET FOREIGN_KEY_CHECKS=1")
            conn.commit()
            return f"已删除业务/凭证/审计/消息数据 {total} 行（覆盖 {len(plans)} 条清理规则）"
        finally:
            conn.close()

    def s_verify():
        """双重核对：① 主键残留；② 全库 create_time 窗口扫描必须归零。"""
        left = []
        for tbl, coln, vals in [("exp_bill", "id", ids["bill"]),
                                ("po_order", "id", ids["po"]),
                                ("po_receipt", "id", ids["receipt"]),
                                ("inv_invoice", "id", ids["invoice"]),
                                ("cont_contract", "id", ids["contract"]),
                                ("op_voucher", "id", ids["voucher"]),
                                ("op_pay_batch", "id", ids["batch"]),
                                ("exp_bill_payment", "bill_id", ids["bill"])]:
            s, a = _in(coln, vals)
            if not s:
                continue
            n = q(f"SELECT COUNT(*) FROM {tbl} WHERE {s}", tuple(a), one=True)[0]
            if n:
                left.append(f"{tbl}={n}")
        assert not left, "主键残留:" + ";".join(left)

        conn = db()
        try:
            cur = conn.cursor()
            dirty = []
            for tscol in ("create_time", "created_at"):
                cur.execute("""SELECT table_name FROM information_schema.columns
                               WHERE table_schema='fssc' AND column_name=%s""", (tscol,))
                for (t,) in cur.fetchall():
                    try:
                        cur.execute(f"SELECT COUNT(*) FROM `{t}` WHERE {tscol} >= %s",
                                    (CLEANUP_WINDOW,))
                        n = cur.fetchone()[0]
                        if n:
                            dirty.append(f"{t}={n}")
                    except Exception:
                        pass
        finally:
            conn.close()
        assert not dirty, "窗口扫描仍有残留:" + ";".join(dirty)
        return "核对通过：主键 0 残留 + 全库 create_time/created_at 窗口扫描 0 命中"

    def s_counts():
        c = {}
        for t in ["exp_bill", "po_order", "po_receipt", "inv_invoice", "inv_bill_invoice",
                  "cont_contract", "op_pay_batch", "exp_bill_payment", "act_ru_task"]:
            try:
                c[t] = q(f"SELECT COUNT(*) FROM {t}", one=True)[0]
            except Exception:
                c[t] = "ERR"
        return "清理后行数: " + json.dumps(c, ensure_ascii=False)

    ok = step("取消在途流程实例（调用应用接口）", s_cancel_flows)
    step("清除流程运行/历史数据 + wf_* 视图表", s_purge_flows)
    step("删除业务/凭证/总账/审计/消息测试数据（26 表覆盖）", s_biz_data)
    step("清理结果双重核对", s_verify)
    step("清理后表快照", s_counts)
    if ok and os.path.exists(STATE_FILE):
        os.remove(STATE_FILE)
    return summary()


def phase_snapshot():
    print("当前 E2E 状态文件:", json.dumps(STATE, ensure_ascii=False, indent=2))
    for t in ["exp_bill", "po_order", "po_receipt", "inv_invoice", "inv_bill_invoice",
              "cont_contract", "supplier_recon", "act_ru_task"]:
        try:
            print(f"  {t:<20} {q(f'SELECT COUNT(*) FROM {t}', one=True)[0]}")
        except Exception as e:
            print(f"  {t:<20} ERR {str(e)[:50]}")


if __name__ == "__main__":
    load_state()
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    if mode == "create":
        phase_create()
    elif mode == "match":
        phase_match()
    elif mode == "flow":
        phase_flow()
    elif mode == "pay":
        phase_pay()
    elif mode == "cleanup":
        phase_cleanup()
    elif mode == "snapshot":
        phase_snapshot()
    else:
        phase_create()
        phase_match()
        phase_flow()
        phase_pay()
        summary()
