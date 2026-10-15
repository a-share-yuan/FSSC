#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""单文件页数测试：一次只处理一个 vsd，独立新起（由外层调用）。"""
import uno, sys, os
from com.sun.star.beans import PropertyValue

def prop(name, value):
    p = PropertyValue(); p.Name = name; p.Value = value; return p

def main():
    path = sys.argv[1]
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
    ctx = resolver.resolve("uno:socket,host=127.0.0.1,port=2002;urp;StarOffice.ComponentContext")
    desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
    url = uno.systemPathToFileUrl(path)
    flags = (prop("Hidden", True), prop("MacroExecutionMode", 4))
    doc = desktop.loadComponentFromURL(url, "_blank", 0, flags)
    supplier = doc.getDrawPages()
    n = supplier.getCount()
    print(n)
    doc.close(False)

if __name__ == "__main__":
    main()