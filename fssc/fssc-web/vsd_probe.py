#!/usr/bin/env python
# -*- coding: utf-8 -*-
import uno

variants = [
    "uno:socket,host=127.0.0.1,port=2002;urp;StarOffice.ComponentContext",
    "uno:socket,host=127.0.0.1,port=2002;urp;ComponentContext",
    "uno:socket,host=127.0.0.1,port=2002;urp;",
]
local = uno.getComponentContext()
resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
for url in variants:
    try:
        obj = resolver.resolve(url)
        print("OK  :", url, "->", obj)
    except Exception as e:
        print("FAIL:", url, "::", e)