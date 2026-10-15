#!/usr/bin/env python
# -*- coding: utf-8 -*-
import uno
from com.sun.star.beans import PropertyValue
import os
import sys

ROOT = r"D:\上海公司\中集\FSSC流程设计v3.0"

def prop(name, value):
    p = PropertyValue()
    p.Name = name
    p.Value = value
    return p

def connect():
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
    ctx = resolver.resolve("uno:socket,host=127.0.0.1,port=2002;urp;StarOffice.ComponentContext")
    smgr = ctx.ServiceManager
    desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    return desktop

def load_doc(desktop, path):
    url = uno.systemPathToFileUrl(path)
    flags = (
        prop("Hidden", True),
        prop("ReadOnly", True),
        prop("MacroExecutionMode", 4),  # 4 = ALWAYS_EXECUTE_NO_WARN
    )
    return desktop.loadComponentFromURL(url, "_blank", 0, flags)

def main():
    desktop = connect()
    files = []
    for dirpath, _, names in os.walk(ROOT):
        for n in names:
            if n.lower().endswith(".vsd"):
                files.append(os.path.join(dirpath, n))
    files.sort()
    print("found", len(files), "vsd", flush=True)
    multi = {}
    for f in files:
        stem = os.path.splitext(os.path.basename(f))[0]
        for attempt in range(2):
            try:
                doc = load_doc(desktop, f)
                if doc is None:
                    print(f"ERR\t{stem}\tNone doc", flush=True)
                    break
                supplier = doc.getDrawPages()
                n = supplier.getCount()
                print(f"{n}\t{stem}", flush=True)
                if n > 1:
                    multi[stem] = n
                doc.close(False)
                break
            except Exception as e:
                print(f"ERR\t{stem}\t{e}", flush=True)
                # 桥断开，重连后重试
                try:
                    desktop = connect_fn()
                except Exception:
                    pass
                desktop = connect()
    print("--- 多页文件:", len(multi), "---", flush=True)
    for k, v in multi.items():
        print(f"  {v} 页：{k}", flush=True)

if __name__ == "__main__":
    main()