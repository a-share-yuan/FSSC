#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从 .vsd 流程图提取流程/节点信息，每个流程输出一个 CSV（utf-8-sig，Excel 可直接开）。

从 Visio 泳道图提取：
  流程名称 = 顶部大标题
  节点    = 带编号的活动框（如 "010 提交报账单"）
  角色    = 该节点所在泳道（横向满宽 Lane 分组，含单个角色文本）
  下一节点 = 按编号递增的下一个节点
  操作    = 节点动作文字（去掉编号前缀）
  页面/路由/附件/OCR/异常 = 图中无此信息，留空占位（供后续手工补填）

soffice 自管理，server 崩溃自动重启续扫。
"""
import uno, os, subprocess, time, socket, csv, re, sys
from com.sun.star.beans import PropertyValue

ROOT = r"D:\上海公司\中集\FSSC流程设计v3.0"
OUT  = r"D:\上海公司\中集\FSSC流程设计v3.0_导出"
SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"
PROFILE = r"C:\Libre_Scrap"
PORT = 2002

COLS = ["流程名称", "序号", "节点", "角色", "页面", "路由", "操作", "附件", "OCR", "下一节点", "异常"]
NUM_RE = re.compile(r"^\s*(\d{1,3})\s*(.*)$", re.S)

def prop(name, value):
    p = PropertyValue(); p.Name = name; p.Value = value; return p

def port_open(port, tries=120):
    for _ in range(tries):
        try:
            s = socket.create_connection(("127.0.0.1", port), 0.5); s.close(); return True
        except OSError:
            time.sleep(1)
    return False

def start_soffice():
    import shutil
    if os.path.exists(PROFILE):
        shutil.rmtree(PROFILE, ignore_errors=True)
    cmd = [SOFFICE, "--headless", "--norestore", "--invisible", "--nofirststartwizard",
           "-env:UserInstallation=file:///C:/Libre_Scrap",
           "--accept=socket,host=127.0.0.1,port=%d;urp;" % PORT]
    return subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def resolve(_ctx):
    local = uno.getComponentContext()
    res = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
    return res.resolve("uno:socket,host=127.0.0.1,port=%d;urp;StarOffice.ComponentContext" % PORT)

def get_desktop():
    ctx = resolve(None)
    return ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)

def load(desktop, path):
    url = uno.systemPathToFileUrl(path)
    return desktop.loadComponentFromURL(
        url, "_blank", 0,
        (prop("Hidden", True), prop("MacroExecutionMode", 4), prop("ReadOnly", True)))

def shape_text(sh):
    for a in ("String", "string"):
        try:
            v = sh.getPropertyValue(a)
            if v: return str(v)
        except Exception: pass
    try: return str(sh.getString())
    except Exception: pass
    return ""

def clean(s):
    return "".join(s.splitlines())

def collect_shapes(sh, lst):
    """递归收集所有 TextShape：text + 绝对坐标 + area。"""
    t = shape_text(sh).strip()
    if t:
        try:
            pos = sh.getPosition(); size = sh.getSize()
            lst.append({"text": t, "x": pos.X, "y": pos.Y,
                        "w": size.Width, "h": size.Height,
                        "area": max(1, size.Width) * max(1, size.Height)})
        except Exception:
            lst.append({"text": t, "x": 0, "y": 0, "w": 0, "h": 0, "area": 0})
    try:
        n = sh.getCount()
        for i in range(n):
            collect_shapes(sh.getByIndex(i), lst)
    except Exception:
        pass

def analyse_page(page):
    """返回 {title, roles:[(role,ymin,ymax)], steps:[{num,action,cx,cy}]}"""
    n = page.getCount()
    shapes = []
    lanes = []          # 顶层大 GroupShape（横向泳道）
    for i in range(n):
        g = page.getByIndex(i)
        try:
            pos = g.getPosition(); size = g.getSize()
            gid = "g%d" % i
        except Exception:
            continue
        shapes.append({"g": g, "id": gid,
                       "x": pos.X, "y": pos.Y, "w": size.Width, "h": size.Height,
                       "type": g.getShapeType()})
    # 页面内容范围
    pageW = max((s["x"] + s["w"] for s in shapes), default=20000)
    pageH = max((s["y"] + s["h"] for s in shapes), default=50000)

    # 泳道：横向满宽(top-level Group) 为泳道背景带；角色名在其左侧窄表头分组里。
    # 把每个泳道带与"同一 y 范围、位于左边缘、含文本"的表头配对得到角色名。
    bands = []
    for s in shapes:
        if s["type"] != "com.sun.star.drawing.GroupShape":
            continue
        if s["w"] >= 0.6 * pageW and s["h"] >= 1200:
            bands.append(s)
    roles = []
    for b in bands:
        bc = b["y"] + b["h"] / 2
        best = None
        for s in shapes:
            if s is b or s["type"] != "com.sun.star.drawing.GroupShape":
                continue
            if s["w"] > 0.5 * pageW or s["x"] > 0.3 * pageW:
                continue
            sc = s["y"] + s["h"] / 2
            if abs(sc - bc) > max(200, b["h"] * 0.35):
                continue
            subs = []
            collect_shapes(s["g"], subs)
            nm = " / ".join([clean(x["text"]) for x in subs if clean(x["text"])])
            if not nm or nm in ("开始", "结束") or len(nm) > 12:
                continue
            score = abs(sc - bc) * 1000 + s["x"]
            if best is None or score < best[0]:
                best = (score, nm, s["y"], s["y"] + s["h"])
        if best:
            roles.append({"name": best[1], "ymin": best[2], "ymax": best[3]})

    # 文本全集，找标题 + 带编号节点
    texts = []
    for s in shapes:
        collect_shapes(s["g"], texts)
    # 标题：最靠近顶部且接近整页宽的文本
    title = ""
    cand = [t for t in texts if t["w"] >= 0.5 * pageW and t["y"] < 0.12 * pageH]
    if cand:
        title = clean(min(cand, key=lambda t: t["y"])["text"])
    # 节点：文本以编号开头；取同组中面积最大的那条，避免 icon/编号重复
    steps = []
    best = {}
    for t in texts:
        m = NUM_RE.match(t["text"])
        if not m:
            continue
        num = int(m.group(1))
        if num not in best or t["area"] > best[num]["area"]:
            best[num] = {"num": num, "action": clean(m.group(2)),
                         "cx": t["x"] + t["w"] / 2, "cy": t["y"] + t["h"] / 2}
    steps = [best[k] for k in sorted(best)]

    # 节点排序 & 下一节点：按编号
    order = sorted(steps, key=lambda s: s["num"])
    # 为每个节点找角色（泳道包含其中心，未命中则取最近泳道）
    for i, st in enumerate(order):
        cy = st["cy"]
        lane = None
        for r in roles:
            if r["ymin"] <= cy <= r["ymax"]:
                lane = r
                break
        if lane is None and roles:
            lane = min(roles, key=lambda r: abs(r["ymin"] + (r["ymax"] - r["ymin"]) / 2 - cy))
        st["role"] = lane["name"] if lane else ""
        st["next"] = order[i + 1]["action"] if i + 1 < len(order) else "结束"
    return {"title": title, "order": order}

def write_csv(path, rows):
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(COLS)
        w.writerows(rows)

def main_loop():
    files = []
    single = sys.argv[1] if len(sys.argv) > 1 and os.path.isfile(sys.argv[1]) else None
    if single:
        files = [os.path.abspath(single)]
    else:
        for dp, _, ns in os.walk(ROOT):
            for n in ns:
                if n.lower().endswith(".vsd"):
                    files.append(os.path.join(dp, n))
        files.sort()
    print("files:", len(files), flush=True)

    proc = start_soffice(); port_open(PORT)
    print("soffice started", flush=True)
    idx = 0; ok = fail = 0
    MAX_RETRY = 4  # 单个文件连续崩溃尝试次数
    attempts = 0
    while idx < len(files):
        f = files[idx]
        stem = os.path.splitext(os.path.basename(f))[0]
        try:
            desktop = get_desktop()
        except Exception:
            print("  [restart] conn fail", flush=True)
            try: proc.kill()
            except Exception: pass
            proc = start_soffice(); port_open(PORT); continue
        try:
            doc = load(desktop, f)
            if doc is None:
                print("NONE\t%s" % stem, flush=True); idx += 1; attempts = 0; continue
            pages = doc.getDrawPages(); npg = pages.getCount()
            rows = []
            for pi in range(npg):
                info = analyse_page(pages.getByIndex(pi))
                order = info["order"]
                if not order:
                    rows.append([info["title"], "", "", "", "", "", "", "", "", "", ""]); continue
                for st in order:
                    rows.append([
                        info["title"], st["num"], st["action"], st["role"],
                        "", "", st["action"], "", "", st["next"], "",
                    ])
            rel = os.path.relpath(f, ROOT)
            outdir = os.path.join(OUT, os.path.dirname(rel))
            os.makedirs(outdir, exist_ok=True)
            csvp = os.path.join(outdir, stem + ".csv")
            write_csv(csvp, rows)
            print("OK\t%s\tpages=%d rows=%d\t%s" % (stem, npg, len(rows), csvp), flush=True)
            ok += 1
            try: doc.close(False)
            except Exception: pass
            idx += 1; attempts = 0
        except Exception as e:
            attempts += 1
            if attempts > MAX_RETRY:
                print("FAIL\t%s\t%s" % (stem, e), flush=True); fail += 1
                idx += 1; attempts = 0
                continue
            print("  [retry %d/%d] %s : %s" % (attempts, MAX_RETRY, stem, e), flush=True)
            try:
                desktop.getCurrentComponent().close(False)
            except Exception:
                pass
            try: proc.kill()
            except Exception: pass
            proc = start_soffice(); port_open(PORT)
    print("=== OK=%d FAIL=%d ===" % (ok, fail), flush=True)
    try: proc.kill()
    except Exception: pass

if __name__ == "__main__":
    main_loop()