#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""批量 .vsd -> 每页 PNG + SVG。

关键点：多页 .vsd 需保存为多个单面文件。Draw 的 png/svg 导出只导出
"当前活动页"，因此遍历每页并调用 DrawView.setCurrentPage() 切换到对应页，
再分别 storeToURL 导出，命名规则：
  单页   -> <stem>.png / <stem>.svg
  多页   -> <stem>_p1.png / <stem>_p1.svg ... <stem>_pN.png / <stem>_pN.svg

soffice 由本脚本自管理（独立 profile），server 崩溃则自动重启续扫。
"""
import uno, os, subprocess, time, socket, sys
from com.sun.star.beans import PropertyValue

ROOT = r"D:\上海公司\中集\FSSC流程设计v3.0"
OUT  = r"D:\上海公司\中集\FSSC流程设计v3.0_导出"
SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"
PROFILE = r"C:\Libre_Scrap"
PORT = 2002

# 导出位图宽度（像素），保证流程图的清晰度
PNG_WIDTH = 2400

PNG_FMT = "draw_png_Export"
SVG_FMT = "draw_svg_Export"

def prop(name, value):
    p = PropertyValue(); p.Name = name; p.Value = value; return p

def outdir_for(f):
    """根据源文件在 ROOT 下的相对目录，决定导出目录并创建。"""
    rel = os.path.relpath(f, ROOT)
    d = os.path.join(OUT, os.path.dirname(rel))
    os.makedirs(d, exist_ok=True)
    return d

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
    p = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return p

def resolve(_ctx):
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
    return resolver.resolve("uno:socket,host=127.0.0.1,port=%d;urp;StarOffice.ComponentContext" % PORT)

def get_desktop():
    ctx = resolve(None)
    return ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)

def load(desktop, path):
    url = uno.systemPathToFileUrl(path)
    return desktop.loadComponentFromURL(
        url, "_blank", 0,
        (prop("Hidden", True), prop("MacroExecutionMode", 4), prop("ReadOnly", True)))

def export_page(doc, idx, outdir, stem, npages):
    """把第 idx 页（0 基）导出为 png + svg。
    通过 DrawView.setCurrentPage() 切换到该页后再 storeToURL。
    返回输出文件后缀（单页为空，多页为 "_pN"）。
    """
    pages = doc.getDrawPages()
    page = pages.getByIndex(idx)
    # 切换到当前页：Draw 导出只针对当前活动页
    controller = doc.getCurrentController()
    try:
        controller.setCurrentPage(page)
    except Exception:
        pass  # 某些实现无此接口，忽略，仍走默认首页
    suffix = "" if npages == 1 else "_p%d" % (idx + 1)

    png_path = os.path.join(outdir, "%s%s.png" % (stem, suffix))
    png_url = uno.systemPathToFileUrl(png_path)
    doc.storeToURL(png_url, (
        prop("FilterName", PNG_FMT),
        prop("FilterData", make_png_filter_data()),
    ))

    svg_path = os.path.join(outdir, "%s%s.svg" % (stem, suffix))
    svg_url = uno.systemPathToFileUrl(svg_path)
    doc.storeToURL(svg_url, (prop("FilterName", SVG_FMT),))
    return suffix

def make_png_filter_data():
    """让 draw_png_Export 按固定像素宽度导出，保证清晰度。"""
    try:
        from com.sun.star.beans import PropertyValues
        vals = PropertyValues(3)
        vals[0] = prop("PixelWidth", PNG_WIDTH)
        vals[1] = prop("PixelHeight", 0)          # 0 -> 按宽高比自动
        vals[2] = prop("LogicalWidth", 0)          # 保持原始逻辑尺寸
        return vals
    except Exception:
        return None

def main_loop():
    files = []
    for dp, _, ns in os.walk(ROOT):
        for n in ns:
            if n.lower().endswith(".vsd"):
                files.append(os.path.join(dp, n))
    files.sort()
    print("files:", len(files), flush=True)

    proc = start_soffice()
    port_open(PORT)
    print("soffice started", flush=True)

    idx = 0
    ok = fail = 0
    # 记录每个文件最终导出的数量，便于汇报多页拆分情况
    exported = {}
    MAX_RETRY = 4
    attempts = 0
    while idx < len(files):
        f = files[idx]
        stem = os.path.splitext(os.path.basename(f))[0]
        # 连接（失败则重启 soffice）
        try:
            desktop = get_desktop()
        except Exception:
            print("  [restart soffice] conn failed", flush=True)
            try: proc.kill()
            except Exception: pass
            proc = start_soffice()
            port_open(PORT)
            continue
        try:
            doc = load(desktop, f)
            if doc is None:
                print("NONE\t%s" % stem, flush=True); idx += 1; attempts = 0; continue
            pages = doc.getDrawPages()
            n = pages.getCount()
            for i in range(n):
                export_page(doc, i, outdir_for(f), stem, n)
            exported[stem] = n
            print("OK\t%s\t%d页\t%s" % (stem, n, outdir_for(f)), flush=True)
            ok += 1
            try: doc.close(False)
            except Exception: pass
            idx += 1
            attempts = 0
        except Exception as e:
            attempts += 1
            if attempts > MAX_RETRY:
                print("FAIL\t%s\t%s" % (stem, e), flush=True); fail += 1
                idx += 1; attempts = 0; continue
            print("  [retry %d/%d] %s : %s" % (attempts, MAX_RETRY, stem, e), flush=True)
            # 把可能残留的文档关掉，避免占用
            try:
                desktop.getCurrentComponent().close(False)
            except Exception:
                pass
            # 桥断了，重启并重试本文件
            try: proc.kill()
            except Exception: pass
            proc = start_soffice()
            port_open(PORT)

    print("=== 汇总 ===", flush=True)
    print("OK=%d FAIL=%d" % (ok, fail), flush=True)
    multi = {k: v for k, v in exported.items() if v > 1}
    print("多页文件 %d 个，均已拆分为多个单面：" % len(multi), flush=True)
    for k, v in sorted(multi.items(), key=lambda x: -x[1]):
        print("  %d页  %s" % (v, k), flush=True)
    try: proc.kill()
    except Exception: pass

if __name__ == "__main__":
    main_loop()