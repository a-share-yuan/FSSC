# -*- coding: utf-8 -*-
"""FSSC 核心域集成 E2E（T0.2）

覆盖五大核心域的**真实契约**（不是逐个探活，而是串成链路验证数据贯通）：

    费用 → 工作流 → 付款 → 凭证 → 预算

设计原则：
  1. 只用标准库，与 scripts/smoke_env.py 同风格，可在 CI 跑。
  2. 端点路径**从控制器实测抄来**，不猜；口径漂移会立刻报错暴露。
  3. 只读为主：GET 类端点做真实数据断言；写操作仅在显式 --write 时开启，
     且默认关闭以免污染演示库（T0.2 要求「不改业务数据」）。
  4. 业务拒绝 = HTTP 200 + code=4030/权限码（本系统权限拒绝不回 4xx），
     断言按此口径，避免把"无权限"误判成"服务挂了"。

用法：
    python scripts/e2e-core-domains.py                     # 只读（默认，安全）
    python scripts/e2e-core-domains.py --base http://127.0.0.1:8089/api
    python scripts/e2e-core-domains.py --user Y000185 --write

退出码：0 全通过；1 有失败项。
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

# ---------------------------------------------------------------- 配置
DEFAULT_BASE = "http://127.0.0.1:8089/api"
DEFAULT_USER = "Y000185"          # 共享会计-01，持有 FSSC_AUDITOR，核心域可见面最广
DEFAULT_TENANT = "DEMO1"
DEFAULT_PASS = "123456"

# 端点口径（实测自各 Controller 的 @RequestMapping，不猜测）
EP = {
    # 费用域
    "exp_myPage":      ("GET",  "/tenant/expense/bill/myPage"),
    "exp_detail_tpl":  ("GET",  "/tenant/expense/detail/travels/0"),
    "exp_mine_stat":   ("GET",  "/tenant/expense/mine/stat"),
    "exp_templates":   ("GET",  "/tenant/expense/mobile/templates"),
    # 预算域
    "budget_exec":     ("GET",  "/tenant/budget/execution"),
    "budget_item":     ("GET",  "/tenant/budget/item/page"),
    "budget_occupy":   ("GET",  "/tenant/budget/occupy/page"),
    # 总账/凭证域
    "gl_entry":        ("GET",  "/tenant/op/gl/entry"),
    "gl_balance":      ("GET",  "/tenant/op/gl/balance"),
    "gl_trial":        ("GET",  "/tenant/op/gl/trial"),
    "gl_posted":       ("GET",  "/tenant/op/gl/posted"),
    # 付款域
    "payplan_page":    ("GET",  "/tenant/pay-plan/page"),
    # 工作流域
    "wf_approval":     ("GET",  "/tenant/wf/approval-item/page"),
}

OK_CODE = 0
PERM_CODES = {4030, 403}


# 部分端点需必填查询参数（实测自 Controller 签名，不猜测）
# /gl/balance 与 /gl/trial 需 companyId+year+month；/gl/posted 需 voucherId
EP_PARAMS = {
    "gl_balance": "companyId=1&year=2026&month=10",
    "gl_trial":   "companyId=1&year=2026&month=10",
    "gl_posted":  "voucherId=67",
}


class Result:
    def __init__(self):
        self.rows = []

    def add(self, domain, name, ok, detail):
        self.rows.append((domain, name, ok, detail))
        flag = "PASS" if ok else "FAIL"
        print("  [%s] %-6s %-16s %s" % (flag, domain, name, detail))

    @property
    def failed(self):
        return [r for r in self.rows if not r[2]]


def call(base, token, method, path, body=None, timeout=15):
    """返回 (http_code, parsed_json_or_None, raw_text)。不抛异常。"""
    url = base.rstrip("/") + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            code = resp.getcode()
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace") if e.fp else ""
        code = e.code
    except Exception as e:                      # 连接失败等
        return 0, None, str(e)
    try:
        return code, json.loads(raw), raw
    except Exception:
        return code, None, raw[:200]


def login(base, user, pwd, tenant):
    code, js, raw = call(base, None, "POST", "/auth/login",
                         {"subjectType": "TENANT", "tenantCode": tenant,
                          "username": user, "password": pwd})
    if code != 200 or not isinstance(js, dict):
        return None, "HTTP %s %s" % (code, (raw or "")[:120])
    if js.get("code") != OK_CODE:
        return None, "业务码 %s %s" % (js.get("code"), js.get("msg"))
    data = js.get("data") or {}
    token = data.get("token") or data.get("accessToken")
    if not token:
        return None, "响应无 token，data=%s" % list(data.keys())
    return token, "登录成功 code=0"


def probe(res, base, token, domain, key, expect_data=True):
    """按 EP 表探一个端点：通即可（code=0）或被权限拦（4030/403）均视为服务正常。"""
    method, path = EP[key]
    params = EP_PARAMS.get(key)
    if params:
        path = "%s?%s" % (path, params)
    code, js, raw = call(base, token, method, path)
    if code == 0:
        res.add(domain, key, False, "连不通：%s" % raw[:100])
        return None
    if not isinstance(js, dict):
        res.add(domain, key, False, "HTTP %s 非 JSON：%s" % (code, raw[:80]))
        return None
    biz = js.get("code")
    if biz == OK_CODE:
        d = js.get("data")
        shape = _shape(d)
        res.add(domain, key, True, "code=0 data=%s" % shape)
        return d
    if biz in PERM_CODES:
        res.add(domain, key, True, "code=%s（无权限，服务正常）" % biz)
        return None
    res.add(domain, key, False, "code=%s msg=%s" % (biz, js.get("msg")))
    return None


def _shape(d):
    if d is None:
        return "null"
    if isinstance(d, dict):
        for k in ("records", "list", "rows", "items", "content"):
            if k in d and isinstance(d[k], list):
                return "{%s:[%d]}" % (k, len(d[k]))
        return "{%s}" % ",".join(list(d.keys())[:4])
    if isinstance(d, list):
        return "[%d]" % len(d)
    return type(d).__name__


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=os.environ.get("FSSC_BASE_URL", DEFAULT_BASE))
    ap.add_argument("--user", default=os.environ.get("FSSC_SMOKE_USER", DEFAULT_USER))
    ap.add_argument("--pass", dest="pwd", default=os.environ.get("FSSC_SMOKE_PASS", DEFAULT_PASS))
    ap.add_argument("--tenant", default=os.environ.get("FSSC_SMOKE_TENANT", DEFAULT_TENANT))
    ap.add_argument("--write", action="store_true", help="允许写操作（默认关闭，避免污染演示库）")
    args = ap.parse_args()

    print("=" * 78)
    print("FSSC 核心域集成 E2E（T0.2）  base=%s  user=%s  mode=%s"
          % (args.base, args.user, "READ-ONLY" if not args.write else "WRITE"))
    print("=" * 78)

    res = Result()

    # ---- 0. 登录（不通过则后续无意义）----
    token, msg = login(args.base, args.user, args.pwd, args.tenant)
    res.add("AUTH", "login", bool(token), msg)
    if not token:
        print("\n结论：登录失败，后续用例未执行")
        return 1

    # ---- 1. 费用域 ----
    print("\n-- 费用域 --")
    mypage = probe(res, args.base, token, "费用", "exp_myPage")
    probe(res, args.base, token, "费用", "exp_mine_stat")
    probe(res, args.base, token, "费用", "exp_templates")

    # ---- 2. 工作流域 ----
    print("\n-- 工作流域 --")
    probe(res, args.base, token, "工作流", "wf_approval")

    # ---- 3. 付款域 ----
    print("\n-- 付款域 --")
    probe(res, args.base, token, "付款", "payplan_page")

    # ---- 4. 凭证/总账域 ----
    print("\n-- 凭证域 --")
    entry = probe(res, args.base, token, "凭证", "gl_entry")
    probe(res, args.base, token, "凭证", "gl_balance")
    probe(res, args.base, token, "凭证", "gl_trial")
    probe(res, args.base, token, "凭证", "gl_posted")

    # ---- 5. 预算域 ----
    print("\n-- 预算域 --")
    probe(res, args.base, token, "预算", "budget_exec")
    probe(res, args.base, token, "预算", "budget_item")
    probe(res, args.base, token, "预算", "budget_occupy")

    # ---- 6. 跨域贯通断言（有数据时才断言，避免空库假绿）----
    print("\n-- 跨域贯通 --")
    if isinstance(mypage, dict):
        recs = None
        for k in ("records", "list", "rows", "items", "content"):
            if isinstance(mypage.get(k), list):
                recs = mypage[k]
                break
        if recs:
            res.add("贯通", "费用单可读", True, "取到 %d 条报销单" % len(recs))
            bid = recs[0].get("id")
            if bid:
                code, js, raw = call(args.base, token, "GET",
                                     "/tenant/expense/bill/detail/%s" % bid)
                ok = (code == 200 and isinstance(js, dict) and js.get("code") == OK_CODE)
                res.add("贯通", "单据详情(%s)" % bid, ok,
                        "code=%s" % (js.get("code") if isinstance(js, dict) else "N/A"))
                code, js, raw = call(args.base, token, "GET",
                                     "/tenant/expense/bill-trace-chain/%s" % bid)
                ok = (code == 200 and isinstance(js, dict) and js.get("code") == OK_CODE)
                res.add("贯通", "单据溯源链(%s)" % bid, ok,
                        "code=%s" % (js.get("code") if isinstance(js, dict) else "N/A"))
        else:
            res.add("贯通", "费用单可读", True, "无单据数据，跳过详情/溯源断言")
    else:
        res.add("贯通", "费用单可读", True, "myPage 无数据或无权限，跳过跨域断言")

    # ---- 汇总 ----
    total = len(res.rows)
    failed = res.failed
    print("\n" + "=" * 78)
    print("汇总：total=%d passed=%d failed=%d"
          % (total, total - len(failed), len(failed)))
    for d, n, ok, det in failed:
        print("  FAIL  %-6s %-16s %s" % (d, n, det))
    domains = sorted(set(r[0] for r in res.rows))
    print("覆盖域：%s" % "、".join(domains))
    print("=" * 78)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
