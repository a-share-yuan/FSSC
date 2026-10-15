#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""自管理 soffice 的页数统计器：
python 内部启动 soffice(独立 profile) 作为 UNO server，
遍历所有 .vsd 读取 DrawPages 数量，server 崩溃则自动重启续扫。
"""
import uno, os, subprocess, time, socket, sys, shlex

ROOT = r"D:\上海公司\中集\FSSC流程设计v3.0"
SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"
PROFILE = r"C:\Libre_Scrap"
PORT = 2002

def port_open(port, tries=60):
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

proc = start_soffice()
port_open(PORT)
print("soffice started", flush=True)

props = None
try:
    import com.sun.star.beans  # noqa
    from com.sun.star.beans import PropertyValue
    def prop(name, val):
        p = PropertyValue(); p.Name = name; p.Value = val; return p
except Exception:
    def prop(name, val):
        return None

def resolve(_ctx):
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
    return resolver.resolve("uno:socket,host=127.0.0.1,port=%d;urp;StarOffice.ComponentContext" % PORT)

def load_doc(desktop, path):
    url = uno.systemPathToFileUrl(path)
    return desktop.loadComponentFromURL(
        url, "_blank", 0,
        (prop("Hidden", True), prop("MacroExecutionMode", 4), prop("ReadOnly", True)))

def main_loop():
    global proc
    files = []
    for dp, _, ns in os.walk(ROOT):
        for n in ns:
            if n.lower().endswith(".vsd"):
                files.append(os.path.join(dp, n))
    files.sort()
    print("files:", len(files), flush=True)

    idx = 0
    multi = {}
    overall = 0
    while idx < len(files):
        # 连接（若失败就重启 soffice）
        try:
            ctx = resolve(None)
            desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
        except Exception:
            print("  [restart soffice] conn failed", flush=True)
            proc.kill()
            proc = start_soffice()
            port_open(PORT)
            continue
        # 处理从 idx 开始到崩溃前
        progressed = False
        while idx < len(files):
            f = files[idx]
            stem = os.path.splitext(os.path.basename(f))[0]
            try:
                doc = load_doc(desktop, f)
                if doc is None:
                    print("NONE\t%s" % stem, flush=True); idx += 1; continue
                n = doc.getDrawPages().getCount()
                overall += n
                print("%d\t%s" % (n, stem), flush=True)
                if n > 1:
                    multi[stem] = n
                try: doc.close(False)
                except Exception: pass
                idx += 1
                progressed = True
            except Exception as e:
                print("ERR\t%s\t%s" % (stem, e), flush=True)
                # 桥断了，重启并重试本文件
                global proc
                try: proc.kill()
                except Exception: pass
                proc = start_soffice()
                port_open(PORT)
                progressed = False
                break
        if not progressed:
            break  # 防死循环
    print("=== 多页文件 %d 个 ===" % len(multi), flush=True)
    for k, v in multi.items():
        print("  %d页  %s" % (v, k), flush=True)
    print("TOTAL pages:", overall, flush=True)
    try: proc.kill()
    except Exception: pass

main_loop()