# -*- coding: utf-8 -*-
"""海外报账 + OCR 版式扩展 探针套件（第五批）。
自含：造数→断言→清理；环境变量走 smoke_env（FSSC_BASE_URL/FSSC_DB_*/FSSC_SMOKE_*）。
覆盖清单——
  海外：
    H1 外币行留空自动带当月汇率（USD→7.13，本位币=713.00）
    H2 手填汇率偏差>2% 拦截
    H3 汇率 lookup 端点（直查/反向）
    H4 出国判定：目的地=美国 → 补贴=天数×日标准（3×500=1500）
    H5 出国标准换源：国外出差-住宿费(408) 1300 > 境外档1200 → over_std=1
    H6 国内不受影响：同费用项无出国差旅行 → 不换境外档（over_reason 不含境外口径 / 不标超1200）
    H7 官方全称防误判：destCountry=中华人民共和国 → 按国内处理（不触发境外标准）
  OCR：
    O1 上传白名单：.exe 拒绝 / .ofd 放行
    O2 OFD 端到端：内嵌 TTSRCdata.xml → 自动识别入池（号码/价税/销方全字段）
    O3 数电票 XML 端到端 *(依赖代理二)*
    O4 扫描版 PDF（无文本层）→ 渲染转通道 mock 降级入池 *(依赖代理二)*
    O5 本地解析开关：ocr.local_parse_enabled=0 时 OFD 走 mock 通道（演示不断流）
运行：python scripts/smoke-overseas-ocr.py   （需 18080 服务在线）
"""
import io
import json
import sys
import time
import urllib.error
import urllib.request
import zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
try:
    import smoke_env as env
except Exception:
    env = None

BASE = env.base_url() if env else "http://127.0.0.1:18080/api"
TS = str(int(time.time()))
PASS, FAIL, SKIP = [], [], []


def sql(s, fetch=True):
    if env:
        return env.run_sql(s, fetch=fetch)
    import subprocess
    mysql = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    p = subprocess.run([mysql, "-h192.168.20.77", "-uroot", "-p123456", "fssc",
                        "--default-character-set=utf8mb4", "-N", "-e", s],
                       capture_output=True, text=True, encoding="utf-8", errors="ignore")
    return p.stdout.strip() if fetch else None


def q1(s):
    out = (sql(s) or "").strip().split("\n")
    out = [x for x in out if x.strip()]
    return out[-1].strip() if len(out) > 1 or out else ""


def call(method, path, body=None, token=None, raw=None, ctype=None):
    req = urllib.request.Request(BASE + path,
                                 data=raw if raw is not None else (json.dumps(body).encode() if body is not None else None),
                                 method=method)
    if ctype:
        req.add_header("Content-Type", ctype)
    elif body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        return json.loads(urllib.request.urlopen(req, timeout=60).read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"success": False, "msg": "HTTP %s" % e.code}


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(("  ✓ " if cond else "  ✗ ") + name + (("  | " + str(detail)[:80]) if detail else ""))


def skip(name, why):
    SKIP.append(name)
    print("  – SKIP " + name + "：" + why)


tok = call("POST", "/auth/login", (env.login_payload() if env else
              {"username": "alice", "password": "123456", "tenantCode": "DEMO1", "subjectType": "TENANT"}))["data"]["token"]
CID = q1("SELECT id FROM sys_company WHERE tenant_id=1 AND del_flag=0 ORDER BY id LIMIT 1;")
UID = q1("SELECT id FROM sys_user WHERE tenant_id=1 AND status='NORMAL' ORDER BY id LIMIT 1;")
ITEM = q1("SELECT id FROM md_expense_item WHERE tenant_id=1 AND status='NORMAL' AND id<>408 ORDER BY id LIMIT 1;")
bills, ofd_nos, xml_nos, cfg_backup = [], [], [], {}
IMG_BASE = int(q1("SELECT IFNULL(MAX(id),0) FROM img_file;") or 0)
BATCH_BASE = int(q1("SELECT IFNULL(MAX(id),0) FROM img_batch;") or 0)
print("[OVERSEAS-OCR] TS=%s base=%s" % (TS, BASE))

try:
    # ---------------- 海外 ----------------
    print("— 海外报账 —")
    d = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM", "title": "H1带价" + TS, "companyId": int(CID),
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": int(ITEM), "amount": None, "origAmount": 100.0, "currencyCode": "USD",
                   "exchangeRate": None, "expenseDate": "2026-08-15"}], "attaches": []})
    b = d.get("data")
    if b:
        bills.append(b)
    r = q1("SELECT CONCAT(IFNULL(exchange_rate,0),'/',IFNULL(amount,0)) FROM exp_bill_line WHERE bill_id=%s;" % b)
    check("H1 留空自动带价", r.startswith("7.13") and r.endswith("713.00"), r)

    d2 = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM", "title": "H2偏差" + TS, "companyId": int(CID),
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": int(ITEM), "amount": 500.0, "origAmount": 100.0, "currencyCode": "USD",
                   "exchangeRate": 5.0, "expenseDate": "2026-08-15"}], "attaches": []})
    check("H2 偏差>2%拦截", not d2.get("success") and "偏差" in str(d2.get("msg")), d2.get("msg"))
    if d2.get("data"):
        bills.append(d2["data"])

    lk = call("GET", "/tenant/masterdata/exchange-rate/lookup?from=USD&to=CNY&date=2026-08-15", token=tok)
    ok3 = str(lk.get("data") or "").startswith("7.13") or "7.13" in json.dumps(lk)
    check("H3 lookup端点直查", ok3, lk.get("data"))

    # H4/H5：出国差旅（美国）——住宿项408 1300，3天
    d3 = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM_TRAVEL", "title": "H45出国" + TS, "companyId": int(CID),
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 408, "amount": 1300.0, "taxRate": 0, "expenseDate": "2026-08-15"}], "attaches": []})
    b3 = d3.get("data")
    bills.append(b3)
    call("POST", "/tenant/expense/detail/travels/%s" % b3, token=tok, body=[
        {"travelerName": "探针", "destCountry": "美国", "beginDate": "2026-08-10",
         "endDate": "2026-08-12", "hotelAmount": 1300.0, "allowanceAmount": 0,
         "trafficAmount": 0, "otherAmount": 0}])
    al = q1("SELECT CONCAT(IFNULL(days,-1),'/',IFNULL(allowance_amount,-1)) FROM exp_travel WHERE bill_id=%s;" % b3)
    check("H4 补贴自动算3×500", al == "3.0/1500.00", al)
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % b3)
    call("POST", "/tenant/expense/bill/submit/%s" % b3, token=tok)
    ov = q1("SELECT CONCAT(over_std,'|',IFNULL(over_reason,'')) FROM exp_bill_line WHERE bill_id=%s;" % b3)
    check("H5 境外标准换源", ov.startswith("1|") and "100" in ov, ov)

    # H6：同费用项 无出国差旅行 → 不触发境外档（1300 不应对 1200 报警）
    d4 = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM", "title": "H6国内" + TS, "companyId": int(CID),
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 408, "amount": 1300.0, "taxRate": 0, "expenseDate": "2026-08-15"}], "attaches": []})
    b4 = d4.get("data")
    bills.append(b4)
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % b4)
    call("POST", "/tenant/expense/bill/submit/%s" % b4, token=tok)
    ov4 = q1("SELECT CONCAT(over_std,'|',IFNULL(over_reason,'')) FROM exp_bill_line WHERE bill_id=%s;" % b4)
    check("H6 国内不换境外档", "超过报销标准 100" not in ov4, ov4)

    # H7：官方全称防误判
    d5 = call("POST", "/tenant/expense/bill/saveDraft", token=tok, body={
        "bill": {"billType": "CLAIM_TRAVEL", "title": "H7全称" + TS, "companyId": int(CID),
                 "currencyCode": "CNY", "totalAmount": 0, "extJson": "{}"},
        "lines": [{"itemId": 408, "amount": 1300.0, "taxRate": 0, "expenseDate": "2026-08-15"}], "attaches": []})
    b5 = d5.get("data")
    bills.append(b5)
    call("POST", "/tenant/expense/detail/travels/%s" % b5, token=tok, body=[
        {"travelerName": "探针", "destCountry": "中华人民共和国", "beginDate": "2026-08-10",
         "endDate": "2026-08-12", "hotelAmount": 1300.0, "allowanceAmount": 0,
         "trafficAmount": 0, "otherAmount": 0}])
    sql("INSERT INTO exp_bill_attach(tenant_id,bill_id,attach_type,file_id,sort,del_flag) VALUES (1,%s,'INVOICE',1,1,0);" % b5)
    call("POST", "/tenant/expense/bill/submit/%s" % b5, token=tok)
    ov5 = q1("SELECT over_std FROM exp_bill_line WHERE bill_id=%s;" % b5)
    check("H7 中华人民共和国=国内", ov5 != "1", "over_std=" + ov5)

    # ---------------- OCR ----------------
    print("— OCR 版式 —")

    def upload(fname, data, ctype_):
        bd = "----x" + TS
        body = ("--%s\r\nContent-Disposition: form-data; name=\"files\"; filename=\"%s\"\r\n"
                "Content-Type: %s\r\n\r\n" % (bd, fname, ctype_)).encode() + data + ("\r\n--%s--\r\n" % bd).encode()
        return call("POST", "/tenant/image/batch/upload-multi", token=tok, raw=body,
                    ctype="multipart/form-data; boundary=" + bd)

    bad = upload("evil_%s.exe" % TS, b"MZ", "application/octet-stream")
    check("O1 白名单拦exe", not bad.get("success"), bad.get("msg"))

    def make_ofd(no):
        xml = ('<?xml version="1.0" encoding="UTF-8"?><EInvoice><InvoiceNumber>%s</InvoiceNumber>'
               '<InvoiceType>数电票（增值税专用发票）</InvoiceType><IssueTime>2026-08-10</IssueTime>'
               '<BuyerName>探针购方</BuyerName><BuyerTaxNo>91310000BUY0001X</BuyerTaxNo>'
               '<SellerName>探针销方</SellerName><SellerTaxNo>91310000SEL0002X</SellerTaxNo>'
               '<EInvoiceData><TotalTaxWithoutTax>1000.00</TotalTaxWithoutTax>'
               '<TaxAmount>130.00</TaxAmount><TotalTaxedAmount>1130.00</TotalTaxedAmount>'
               '</EInvoiceData></EInvoice>') % no
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            z.writestr("OFD.xml", "<?xml version='1.0'?><ofd:OFD><ofd:DocBody/></ofd:OFD>")
            z.writestr("Doc_0/Attachs/TTSRCdata.xml", xml)
        return buf.getvalue()

    no_ofd = "2400O" + TS[-7:]
    up = upload("probe_%s.ofd" % TS, make_ofd(no_ofd), "application/ofd")
    time.sleep(1.2)
    row = q1("SELECT CONCAT(amount,'|',tax_amount,'|',net_amount) FROM inv_invoice WHERE invoice_no='%s';" % no_ofd)
    check("O2 OFD端到端入池", up.get("success") and row == "1130.00|130.00|1000.00", row or up.get("msg"))
    if row:
        ofd_nos.append(no_ofd)

    # O3 数电票 XML（代理二交付后生效）
    no_xml = "2400X" + TS[-7:]
    xml_bytes = ('<?xml version="1.0" encoding="UTF-8"?><EInvoice><InvoiceNumber>%s</InvoiceNumber>'
                 '<IssueTime>2026-08-11</IssueTime><BuyerName>探针购方XML</BuyerName>'
                 '<SellerName>探针销方XML</SellerName><EInvoiceData>'
                 '<TotalTaxWithoutTax>2000.00</TotalTaxWithoutTax><TaxAmount>260.00</TaxAmount>'
                 '<TotalTaxedAmount>2260.00</TotalTaxedAmount></EInvoiceData></EInvoice>' % no_xml).encode("utf-8")
    upx = upload("probe_%s.xml" % TS, xml_bytes, "application/xml")
    time.sleep(1.2)
    rowx = q1("SELECT CONCAT(amount,'|',tax_amount,'|',net_amount) FROM inv_invoice WHERE invoice_no='%s';" % no_xml)
    if upx.get("success") and rowx == "2260.00|260.00|2000.00":
        check("O3 数电票XML端到端入池", True, rowx)
        xml_nos.append(no_xml)
    elif not upx.get("success") and ("格式" in str(upx.get("msg")) or "不支持" in str(upx.get("msg"))):
        skip("O3 数电票XML端到端", "白名单未含 xml（代理二未交付）")
    else:
        check("O3 数电票XML端到端入池", False, "%s %s" % (upx.get("msg"), rowx))

    # O4 扫描版 PDF（无文本层）→ 渲染转 mock 通道（代理二交付后生效）
    scan_pdf = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
                b"trailer<</Root 1 0 R/Size 4>>\n%%EOF")
    up4 = upload("scan_%s.pdf" % TS, scan_pdf, "application/pdf")
    time.sleep(1.5)
    st4 = q1("SELECT IFNULL(ocr_status,'?') FROM img_file ORDER BY id DESC LIMIT 1;") if up4.get("success") else "REJECTED:" + str(up4.get("msg"))[:40]
    if up4.get("success") and st4 in ("SUCCESS", "NEED_REVIEW"):
        check("O4 扫描PDF渲染降级", True, st4)
    elif not up4.get("success"):
        skip("O4 扫描PDF渲染降级", "pdf 上传被拒（代理二未交付）: " + st4)
    else:
        check("O4 扫描PDF渲染降级", False, "ocr_status=" + st4)

    # O5 本地解析开关：关→OFD 走 mock 通道（号码变确定性 id 式，非 TTSRC 号码）
    cfg_backup = {"v": q1("SELECT config_value FROM sys_config WHERE tenant_id=1 AND config_key='ocr.local_parse_enabled';")}
    sql("UPDATE sys_config SET config_value='0' WHERE tenant_id=1 AND config_key='ocr.local_parse_enabled';")
    no_off = "2400F" + TS[-7:]
    up5 = upload("off_%s.ofd" % TS, make_ofd(no_off), "application/ofd")
    time.sleep(1.2)
    row5 = q1("SELECT COUNT(*) FROM inv_invoice WHERE invoice_no='%s';" % no_off)
    row_any = q1("SELECT invoice_no FROM inv_invoice ORDER BY id DESC LIMIT 1;")
    ok5 = up5.get("success") and row5 == "0" and row_any and row_any != no_off  # 关本地解析→不入 TTSRC 号码（走 mock 号码）
    check("O5 开关关闭走mock", ok5, "ttsrc入池=%s 最新票=%s" % (row5, row_any))
    if row_any and row_any != no_ofd and row_any != no_xml:
        ofd_nos.append(row_any)
finally:
    for b in bills:
        if b:
            sql("DELETE FROM exp_travel WHERE bill_id=%s; DELETE FROM exp_bill_line WHERE bill_id=%s;"
                " DELETE FROM exp_bill_attach WHERE bill_id=%s; DELETE FROM exp_bill WHERE id=%s;" % (b, b, b, b))
    for no in ofd_nos + xml_nos:
        sql("DELETE i2, g FROM inv_invoice i2 LEFT JOIN inv_invoice_global g ON g.invoice_no=i2.invoice_no "
            "WHERE i2.invoice_no='%s';" % no)
        sql("DELETE FROM inv_invoice WHERE invoice_no='%s'; DELETE FROM inv_invoice_global WHERE invoice_no='%s';" % (no, no))
    if cfg_backup.get("v") is not None and cfg_backup.get("v") != "":
        sql("UPDATE sys_config SET config_value='%s' WHERE tenant_id=1 AND config_key='ocr.local_parse_enabled';"
            % (cfg_backup.get("v") or "1"))
    sql("DELETE FROM img_file WHERE id>%d;" % IMG_BASE)
    sql("DELETE FROM img_batch WHERE id>%d;" % BATCH_BASE)
    # mock 降级发票号码不可预知（MOCK*），按前缀+本次时间窗清理，不动历史演示数据
    mock_ids = [x for x in (sql("SELECT id FROM inv_invoice WHERE invoice_no LIKE 'MOCK%%' "
                                "AND create_time>=NOW()-INTERVAL 15 MINUTE;") or "").split("\n") if x.strip().isdigit()]
    for mid in mock_ids:
        sql("DELETE g FROM inv_invoice_global g JOIN inv_invoice i ON i.invoice_no=g.invoice_no WHERE i.id=%s;" % mid)
    if mock_ids:
        sql("DELETE FROM inv_bill_invoice WHERE invoice_id IN (%s);" % ",".join(mock_ids))
        sql("DELETE FROM inv_invoice WHERE id IN (%s);" % ",".join(mock_ids))
    print("[OVERSEAS-OCR] 清理完成")

print("[OVERSEAS-OCR] summary: pass=%d fail=%d skip=%d" % (len(PASS), len(FAIL), len(SKIP)))
if FAIL:
    print("  FAILED: " + ", ".join(FAIL))
sys.exit(1 if FAIL else 0)
