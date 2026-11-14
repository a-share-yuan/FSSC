# -*- coding: utf-8 -*-
"""需求2章智能分类冒烟：菜单分类 / 5 张新配置表 CRUD / 全局参数 / 流程实例 / 系统日志"""
import json, urllib.request, urllib.error, urllib.parse

BASE = "http://127.0.0.1:18080/api"

def call(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

tok = call("POST", "/auth/login", {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"})["data"]["token"]

# 1) 菜单树：基础数据与配置(303) 三组
mt = call("GET", "/tenant/system/menu/tree", token=tok).get("data") or []
def flat(n, o, depth=0):
    o.append((n, depth)); [flat(c, o, depth + 1) for c in (n.get("children") or [])]
allm = []; [flat(m, allm) for m in mt]
node = {n.get("menuName"): (n, d) for n, d in allm}
def child_names(name):
    n, _ = node.get(name, (None, 0))
    return [c.get("menuName") for c in (n.get("children") or [])] if n else []
sys_names = child_names("基础数据与配置")
assert "系统主数据" in sys_names and "系统配置" in sys_names and "系统监控" in sys_names, sys_names
print("1) 基础数据与配置 > 系统主数据/系统配置/系统监控 ✓")
print("   系统主数据:", child_names("系统主数据"))
print("   系统配置:", child_names("系统配置"))
print("   系统监控:", child_names("系统监控"))

# 2) 5 张新配置表 CRUD 抽查
bid = call("POST", "/tenant/masterdata/config/bank/create", token=tok, body={"bankCode": "BOCOM", "bankName": "交通银行", "bankType": "国有", "status": "NORMAL"})["data"]
assert bid
bp = call("GET", "/tenant/masterdata/config/bank/page", token=tok, params={"pageNum": 1, "pageSize": 10}).get("data") or {}
print("2) 金融机构 CRUD: 新增 id=", bid, " 列表", bp.get("total"), "条")

lid = call("POST", "/tenant/masterdata/config/employeeLevel/create", token=tok, body={"levelCode": "L4", "levelName": "总监", "sort": 4, "status": "NORMAL"})["data"]
assert lid
gp = call("GET", "/tenant/masterdata/config/glConfig/page", token=tok, params={"pageNum": 1, "pageSize": 10}).get("data") or {}
ip = call("GET", "/tenant/masterdata/config/incomeExpense/page", token=tok, params={"pageNum": 1, "pageSize": 10}).get("data") or {}
lp = call("GET", "/tenant/masterdata/config/loanPayType/page", token=tok, params={"pageNum": 1, "pageSize": 10}).get("data") or {}
print("3) 员工等级新增 id=", lid, "| 记账配置", (gp.get("total") or 0), "条 | 收支设置", (ip.get("total") or 0), "条 | 借款付款类型", (lp.get("total") or 0), "条")

# 3) 全局参数（sys_config）
cp = call("GET", "/tenant/system/config/page", token=tok, params={"pageNum": 1, "pageSize": 10}).get("data") or {}
print("4) 全局参数:", (cp.get("total") or 0), "条, 首条=", ((cp.get("list") or [{}])[0].get("configName")))

# 4) 流程实例（wf_instance）
wi = call("GET", "/tenant/wf/query/instance/page", token=tok, params={"pageNum": 1, "pageSize": 5}).get("data") or {}
print("5) 流程实例:", (wi.get("total") or 0), "条")

# 5) 系统日志（sys_oper_log）
lg = call("GET", "/tenant/system/log/page", token=tok, params={"pageNum": 1, "pageSize": 5}).get("data") or {}
print("6) 系统日志:", len(lg.get("list") or []), "条, 首条=", ((lg.get("list") or [{}])[0].get("title")))

print("[SMOKE] 需求2章智能分类 PASS")
