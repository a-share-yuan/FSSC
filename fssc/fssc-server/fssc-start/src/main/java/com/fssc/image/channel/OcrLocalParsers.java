// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 发票影像"本地版式解析"路由器（纯静态、无 Spring 依赖，便于单测）。
 *
 * <p>按文件扩展名分派，PDF/OFD 直接从文件字节解析票面，无需图像 OCR：</p>
 * <ul>
 *   <li>{@code .pdf}：Apache PDFBox {@link PDFTextStripper} 抽取文本层 → 中文标签正则解析
 *       （数电票版式文本顺序稳定）；文本层去空白后过短判定为"扫描版 PDF"→ {@link PDFRenderer} 渲染首页 PNG，
 *       返回 {@link OcrResult#isScanned()}=true + renderedImageBytes，由调用方转图像 OCR 通道（渲染失败→ ok=false）；</li>
 *   <li>{@code .ofd}：OFD 本质是 ZIP，零第三方依赖（{@link ZipInputStream} + 内置 DOM）——
 *       优先读数电票内嵌 XML（{@code Doc_0/Attachs/TTSRCdata.xml} 等 {@code *TTSRC*}/{@code *invoice*}.xml）
 *       按标准节点映射；无内嵌 XML 则拼接 {@code Content.xml} 的 {@code TextCode} 文本后走中文标签正则；</li>
 *   <li>{@code .xml}：电子发票服务平台下载的数电票原始 XML，内置 DOM（禁 DTD 防 XXE）按本地名同义词表容错映射；</li>
 *   <li>图片（jpg/png/...）不在此处理，维持走 {@link OcrChannel}（mock/http）。</li>
 * </ul>
 *
 * <p>输出复用 {@link OcrResult}（rawText=抽取全文；invoiceType 按票面字样推断 VAT_SPECIAL/VAT_NORMAL/OTHER；
 * 数电票无发票代码 → invoiceCode 置空串）。日期/金额归一与 {@link OcrFieldMapper} 复用同一实现。
 * 约定：解析失败不抛穿，返回 ok=false + errorMsg。</p>
 */
@Slf4j
public final class OcrLocalParsers {

    private OcrLocalParsers() {
    }

    /** 本地版式解析支持的扩展名 */
    private static final String PDF = "pdf";
    private static final String OFD = "ofd";
    /** 数电票原始 XML 扩展名（公开，供 OcrService 分派判定） */
    public static final String EXT_XML = "xml";
    /** 文本层去空白后短于此长度即判定为"扫描版 PDF"（无有效文本层） */
    private static final int SCANNED_MIN_TEXT = 30;
    /** 扫描版 PDF 首页渲染分辨率 */
    private static final float SCAN_DPI = 150f;

    // ===== 中文标签正则（容忍标签内空格与全/半角冒号；数电票版式文本顺序稳定）=====
    // 增值税发票号 / 车票航空报销凭证号 通用：数字8~20位（部分长途票可能短于8，放宽到6位）
    // 标签与数字间允许夹 0~6 个非数字杂字（PDF 文本层会把"制通发票""监制"等混入号码行，如"发票号码:制通发票37697930"）
    private static final Pattern P_INVOICE_NO =
            Pattern.compile("(?:发\\s*票|凭\\s*证|票\\s*)号\\s*码?\\s*[:：]?\\s*[^0-9\\r\\n]{0,6}?([0-9]{6,20})");
    private static final Pattern P_INVOICE_DATE =
            Pattern.compile("(?:开\\s*票|购\\s*买|出\\s*行|乘车|乘机)日\\s*期?\\s*[:：]?\\s*(\\d{4}\\s*[年/\\-]\\s*\\d{1,2}\\s*[月/\\-]\\s*\\d{1,2}\\s*日?|\\d{8})");
    /** 合计行：(?<!税)排除"价税合计"，捕获 [不含税金额, 税额] 两段 */
    private static final Pattern P_TOTAL_ROW = Pattern.compile(
            "(?<!税)合\\s*计\\s*[:：]?\\s*[¥￥]?\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*[¥￥]?\\s*([0-9][0-9,]*\\.?[0-9]*)");
    private static final Pattern P_AMOUNT_SMALL = Pattern.compile(
            "小写\\s*[）)]?\\s*[:：]?\\s*[¥￥]?\\s*([0-9][0-9,]*\\.?[0-9]*)");
    private static final Pattern P_AMOUNT_TOTAL_FALLBACK = Pattern.compile(
            "价\\s*税\\s*合\\s*计[^0-9]*([0-9][0-9,]*\\.?[0-9]*)");
    private static final Pattern P_TOTAL_AMOUNT_FALLBACK = Pattern.compile(
            "总\\s*额?\\s*[^0-9]*([0-9][0-9,]*\\.?[0-9]*)");
    /** 运输/住宿票据末段金额兜底：票价/客票款/车票款/机票款/行程单/收费/费用 ￥317.50（客运类票面无"合计/小写"列） */
    private static final Pattern P_AMOUNT_FARE = Pattern.compile(
            "(?:票\\s*价|客\\s*票\\s*款|车\\s*票\\s*款|机\\s*票\\s*款|行\\s*程\\s*单|收\\s*费|费\\s*用)\\s*[:：]?\\s*[¥￥]?\\s*([0-9][0-9,]*\\.?[0-9]*)");
    /** 名称捕获：非贪婪到行尾/下一标签前，避免把同行混入的"销 名称：…"或密文一并吞入（PDF 文本层购买/销售方可能同行） */
    private static final Pattern P_NAME = Pattern.compile(
            "名\\s*称\\s*[:：]?\\s*([^\\r\\n]+?)(?=\\s*(?:名\\s*称|纳税人|识别号|税号|地址|电话|开户行|账号|\\r?\\n|$))");
    /** 数电票货物行首要货名：*大类*具体名* 星号对（可多行，仅取首条）；用于报销按商品名智能匹配费用类型 */
    private static final Pattern P_ITEM_PAIR = Pattern.compile(
            "\\*\\s*([^\\r\\n*]{1,20}?)\\s*\\*\\s*([^\\r\\n*]{1,40}?)(?:\\s|\\r?\\n|$)");
    private static final Pattern P_ITEM_STAR = Pattern.compile("\\*\\s*([^\\r\\n*]{1,40}?)\\s*(?:\\*|\\r?\\n)");
    private static final Pattern P_TAX_NO = Pattern.compile(
            "(?:统一社会信用代码|纳税人识别号|纳税人识别码|税号)[^0-9A-Za-z]*([0-9A-Za-z]{15,20})");
    /** 标签兜底：货物或应税劳务、服务名称 后的首行文本（退化为取首出现，若被表头杂质命中则由 isItemNameUsable 过滤） */
    private static final Pattern P_ITEM_LABEL = Pattern.compile(
            "货物或应税劳务[、，]?\\s*服务名称?[^\\r\\n]{0,16}\\r?\\n\\s*([^\\r\\n]{2,40})");
    /** 数电票/OFD XML 中货物行节点名（含命名空间无关），按序取首 */
    private static final String[] ITEM_NAME_NODES = {
            "CommodityName", "GoodsName", "GoodsNm", "ItemName", "ServiceName",
            "IdItem", "FruitName", "ProjectName" };

    /** 是否为本地版式解析格式（PDF/OFD/数电票 XML） */
    public static boolean isLocalFormat(String fileName) {
        String ext = extOf(fileName);
        return PDF.equals(ext) || OFD.equals(ext) || EXT_XML.equals(ext);
    }

    /** 字节 + 文件名 → 结构化票面。按扩展名分派；无法解析返回 ok=false（不抛穿）。 */
    public static OcrResult parse(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return OcrResult.fail("文件内容为空");
        }
        String ext = extOf(fileName);
        if (PDF.equals(ext)) {
            String text = extractPdfText(bytes);
            if (looksScanned(text)) {
                OcrResult scanned = new OcrResult();
                scanned.setOk(false);
                scanned.setScanned(true);
                byte[] png = renderFirstPageToPng(bytes);
                if (png == null) {
                    scanned.setErrorMsg("PDF 扫描件渲染失败（无法转为图像，请确认 PDF 未损坏）");
                    return scanned;
                }
                scanned.setRenderedImageBytes(png);
                scanned.setErrorMsg("PDF 无文本层（扫描件），已渲染首页 PNG，请转图像 OCR 通道");
                return scanned;
            }
            return parseInvoiceText(text);
        }
        if (OFD.equals(ext)) {
            return parseOfd(bytes);
        }
        if (EXT_XML.equals(ext)) {
            return parseXmlInvoice(bytes);
        }
        return OcrResult.fail("不支持的本地解析格式: " + ext);
    }

    // ============================ PDF ============================

    /** PDFBox 抽取文本层；异常/无文本返回 null（调用方转失败） */
    static String extractPdfText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return text == null || text.isBlank() ? null : text;
        } catch (Exception e) {
            log.warn("PDFBox 文本层抽取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判定是否为"扫描版 PDF"：文本层为 null/空白，或去除所有空白后长度 &lt; {@link #SCANNED_MIN_TEXT}。
     * 纯函数，供分派与单测直接调用（短文本→扫描、长文本→电子）。
     */
    static boolean looksScanned(String text) {
        if (text == null) {
            return true;
        }
        return text.replaceAll("\\s+", "").length() < SCANNED_MIN_TEXT;
    }

    /**
     * 扫描版 PDF：用 PDFBox {@link PDFRenderer} 渲染首页为 PNG（{@value #SCAN_DPI} DPI）。
     * 任何异常（损坏/加密/无页）返回 null，由调用方转 FAILED，不抛穿。
     */
    static byte[] renderFirstPageToPng(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.getNumberOfPages() == 0) {
                return null;
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(0, SCAN_DPI, ImageType.RGB);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bos)) {
                return null;
            }
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("PDF 首页渲染失败: {}", e.getMessage());
            return null;
        }
    }

    // ============================ OFD ============================

    /** OFD（ZIP）解析：内嵌数电票 XML 结构映射优先，退化为 Content.xml TextCode 文本正则。 */
    static OcrResult parseOfd(byte[] bytes) {
        Map<String, byte[]> entries;
        try {
            entries = unzip(bytes);
        } catch (Exception e) {
            return OcrResult.fail("OFD 解压失败: " + e.getMessage());
        }
        String invoiceXml = findInvoiceXml(entries);
        String xmlText = null;
        if (invoiceXml != null) {
            try {
                Document doc = parseXml(entries.get(invoiceXml));
                OcrResult structured = mapStructuredXml(doc);
                xmlText = safeDocText(doc);
                if (structured.getInvoiceNo() != null && !structured.getInvoiceNo().isBlank()) {
                    structured.setRawText(xmlText);
                    structured.setInvoiceType(inferType(xmlText));
                    return structured;
                }
            } catch (Exception e) {
                log.warn("OFD 内嵌 XML 解析失败: {}", e.getMessage());
            }
        }
        String contentText = extractTextCodes(entries);
        String combined = ((xmlText == null ? "" : xmlText) + "\n" + (contentText == null ? "" : contentText)).trim();
        if (combined.isEmpty()) {
            return OcrResult.fail("OFD 未找到数电票内嵌 XML，且无可读文本层（TextCode）");
        }
        return parseInvoiceText(combined);
    }

    /**
     * 定位数电票内嵌 XML（规范位于 {@code Doc_0/Attachs}）：优先 {@code *TTSRC*.xml}，
     * 其次名含 {@code invoice} 的 xml，再次 Attachs 下内容嗅探发票关键字段。
     * 不嗅探 Pages/Content.xml，避免把页面文本层误判为内嵌发票 XML。
     */
    static String findInvoiceXml(Map<String, byte[]> entries) {
        String fallback = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String name = e.getKey().toLowerCase();
            if (!name.endsWith(".xml")) {
                continue;
            }
            if (name.contains("ttsrc")) {
                return e.getKey();
            }
            if (fallback != null) {
                continue;
            }
            if (name.contains("invoice")) {
                fallback = e.getKey();
            } else if (name.contains("attachs")) {
                try {
                    String c = new String(e.getValue(), StandardCharsets.UTF_8);
                    if (c.contains("InvoiceNumber") || c.contains("发票号码") || c.contains("TotalTaxedAmount")
                            // 铁路电子客票内嵌 XML（rai_*.xml）无增值税字样，按其专有节点识别
                            || c.contains("ElectronicInvoiceRailwayETicketNumber")
                            || c.contains("TotalAmountExcludingTax")
                            || c.contains("TypeOfRailwayTicket")) {
                        fallback = e.getKey();
                    }
                } catch (Exception ignore) {
                    // 嗅探失败不影响主流程
                }
            }
        }
        return fallback;
    }

    /** OFD 页面 Content.xml 的 TextCode 文本序列拼接 */
    static String extractTextCodes(Map<String, byte[]> entries) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String name = e.getKey().toLowerCase();
            if (!name.endsWith(".xml") || !name.contains("content")) {
                continue;
            }
            try {
                Document doc = parseXml(e.getValue());
                NodeList nodes = doc.getElementsByTagNameNS("*", "TextCode");
                if (nodes.getLength() == 0) {
                    nodes = doc.getElementsByTagName("TextCode"); // 无命名空间文档退化匹配
                }
                for (int i = 0; i < nodes.getLength(); i++) {
                    String t = nodes.item(i).getTextContent();
                    if (t != null && !t.isBlank()) {
                        sb.append(t.trim()).append('\n');
                    }
                }
            } catch (Exception ignore) {
                // 单个内容文件解析失败：跳过
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 内嵌 XML 标准节点 → OcrResult（多别名容错；namespace-aware 按 localName 取值） */
    static OcrResult mapStructuredXml(Document doc) {
        OcrResult r = new OcrResult();
        r.setInvoiceCode("");
        r.setInvoiceNo(firstText(doc, "InvoiceNumber", "ElectronicInvoiceRailwayETicketNumber",
                "发票号码", "Number"));
        String date = firstText(doc, "IssueTime", "DateOfIssue", "开票日期", "InvoiceDate");
        if (date != null) {
            r.setInvoiceDate(OcrFieldMapper.normalizeDate(date.replaceAll("\\s+", "")));
        }
        // 净额/税额/价税合计：数电票用 Total* 系列；铁路电子客票用 TotalAmountExcludingTax/Fare
        r.setNetAmount(OcrFieldMapper.toDecimal(
                firstText(doc, "TotalTaxWithoutTax", "TotalAmountWithoutTax", "TotalAmountExcludingTax",
                        "合计金额")));
        r.setTaxAmount(OcrFieldMapper.toDecimal(
                firstText(doc, "TotalTax", "TaxAmount", "合计税额")));
        r.setAmount(OcrFieldMapper.toDecimal(
                firstText(doc, "TotalTaxedAmount", "TotalAmount", "Fare", "价税合计")));
        r.setBuyerName(firstText(doc, "BuyerName", "NameOfPurchaser", "PurchaserName", "购买方名称"));
        r.setBuyerTaxNo(firstText(doc, "BuyerIdNum", "BuyerTaxNo", "UnifiedSocialCreditCodeOfPurchaser",
                "PurchaserIdNum", "PurchaserRegisterNum"));
        r.setSellerName(firstText(doc, "SellerName", "SellerName", "销售方名称"));
        r.setSellerTaxNo(firstText(doc, "SellerIdNum", "SellerTaxNo", "SellerRegisterNum"));
        // 首条货名：标准数电票走商品名节点；铁路电子客票无商品名节点，用 出发站→到达站 合成（含"火车"，可被智能规则命中差旅交通费）
        String item = firstGoodsName(doc);
        if (item == null) {
            String from = firstText(doc, "DepartureStation");
            String to = firstText(doc, "DestinationStation");
            if (from != null && to != null) {
                item = "火车 车票 " + from + "→" + to;
            }
        }
        r.setPrimaryItemName(item);
        if (r.getInvoiceNo() == null || r.getInvoiceNo().isBlank()) {
            r.setOk(false);
            r.setErrorMsg("未从 OFD 内嵌 XML 解析到发票号码");
        }
        return r;
    }

    /** 按 localName（忽略命名空间前缀）取首个非空文本；均缺返回 null */
    private static String firstText(Document doc, String... localNames) {
        for (String name : localNames) {
            String v = firstNodeText(doc.getElementsByTagNameNS("*", name));
            if (v == null) {
                // 无命名空间文档：退化用限定名匹配，避免不同 JDK 对 null 命名空间 "*" 匹配差异
                v = firstNodeText(doc.getElementsByTagName(name));
            }
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 遍历节点列表取首个非空文本（元素节点）；无则 null */
    private static String firstNodeText(NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String t = n.getTextContent();
                if (t != null && !t.isBlank()) {
                    return t.trim();
                }
            }
        }
        return null;
    }

    /** 取数电票/OFD XML 首条货物或应税劳务、服务名称（按节点名同义词表取首个可用值） */
    static String firstGoodsName(Document doc) {
        for (String name : ITEM_NAME_NODES) {
            NodeList nodes = doc.getElementsByTagNameNS("*", name);
            if (nodes.getLength() == 0) {
                nodes = doc.getElementsByTagName(name);
            }
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    String t = n.getTextContent();
                    if (t != null && isItemNameUsable(t)) {
                        return t.trim();
                    }
                }
            }
        }
        return null;
    }

    /** 中文标签文本兜底：取首条货名（优先 *大类*具体名* 星号对的具体名，退化 *大类* 星号形式或标签后首行）。
     *  仅在"货物或应税劳务"标签之后搜索——该标签前的文本是发票密文区，含 *xxx+ 等伪星号，会污染货名 */
    static String extractPrimaryItemName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int hdr = text.indexOf("货物或应税劳务");
        String region = hdr >= 0 ? text.substring(hdr) : text;
        // 1) 优先 *大类*具体名*：取具体名（如 *旅游服务*住宿费 → 住宿费）
        Matcher pair = P_ITEM_PAIR.matcher(region);
        if (pair.find()) {
            String specific = pair.group(2).trim();
            if (isItemNameUsable(specific)) {
                return specific;
            }
            String generic = pair.group(1).trim();
            if (isItemNameUsable(generic)) {
                return generic;
            }
        }
        // 2) 退化 *大类* 星号形式
        Matcher m = P_ITEM_STAR.matcher(region);
        if (m.find()) {
            String s = m.group(1).trim();
            if (isItemNameUsable(s)) {
                return s;
            }
        }
        // 3) 标签后首行
        Matcher lm = P_ITEM_LABEL.matcher(region);
        if (lm.find()) {
            String s = lm.group(1).trim();
            if (isItemNameUsable(s)) {
                return s;
            }
        }
        return null;
    }

    /** 货名可用性过滤：去除括号内职务/金额杂质，并剔除票面表头词（规格/数量/金额等） */
    private static boolean isItemNameUsable(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String t = raw.replaceAll("[（(【\\[].*?[）)】\\]]", "").trim();
        if (t.isEmpty()) {
            return false;
        }
        String low = t.replaceAll("\\s+", "");
        return !(low.contains("规格") || low.contains("型号") || low.contains("单位")
                || low.contains("数量") || low.contains("单价") || low.contains("金额")
                || low.contains("税率") || low.contains("税额") || low.contains("价税合计")
                || low.contains("货物或应税劳务"));
    }

    private static String safeDocText(Document doc) {
        try {
            Element root = doc.getDocumentElement();
            return root == null ? null : root.getTextContent();
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                map.put(entry.getName(), bos.toByteArray());
                zis.closeEntry();
            }
        }
        return map;
    }

    private static Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // 防 XXE：安全处理 + 禁 DTD（个别不支持的属性设置失败不影响解析）
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignore) {
            // 个别解析器不支持则跳过
        }
        try {
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (Exception ignore) {
            // 属性不支持不影响本地可信 XML 解析
        }
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml));
    }

    // ============================ 数电票 XML ============================

    /**
     * 电子发票服务平台下载的数电票原始 XML 解析。DOM（{@link #parseXml} 已禁 DTD，防 XXE：含 DOCTYPE
     * 或外部实体的恶意 XML 直接解析失败，不读取外部资源）。节点名容错：按本地名在同义词表内匹配，
     * 不依赖精确路径（namespace-aware {@code getElementsByTagNameNS("*", name)}，无命名空间时退化限定名）。
     * 输出：invoiceCode 置空串、rawText=关键节点拼接文本；解析异常/无票号 → ok=false（不抛穿）。
     */
    static OcrResult parseXmlInvoice(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return OcrResult.fail("XML 内容为空");
        }
        Document doc;
        try {
            doc = parseXml(bytes);
        } catch (Exception e) {
            // 含 DOCTYPE / 外部实体 / 结构非法：显性失败，交由 OcrService 置 FAILED（不降级 mock）
            return OcrResult.fail("XML 结构无法识别（解析失败）: " + e.getMessage());
        }
        OcrResult r = new OcrResult();
        r.setInvoiceCode("");
        r.setInvoiceNo(firstText(doc, "InvoiceNumber", "InvoiceNo", "发票号码", "Number"));
        String date = firstText(doc, "IssueTime", "InvoiceIssueDate", "开票日期", "InvoiceDate");
        if (date != null) {
            r.setInvoiceDate(OcrFieldMapper.normalizeDate(date.replaceAll("\\s+", "")));
        }
        r.setBuyerName(firstText(doc, "BuyerName", "PurchaserName", "购买方名称"));
        r.setBuyerTaxNo(firstText(doc, "BuyerTaxNo", "PurchaserTaxNo", "BuyerIdNum", "PurchaserIdNum",
                "PurchaserRegisterNum", "购买方纳税人识别号"));
        r.setSellerName(firstText(doc, "SellerName", "SellerName2", "SalesorName", "销售方名称"));
        r.setSellerTaxNo(firstText(doc, "SellerTaxNo", "SellerIdNum", "SellerRegisterNum",
                "SalesorIdNum", "销售方纳税人识别号"));
        r.setPrimaryItemName(firstGoodsName(doc));

        BigDecimal net = OcrFieldMapper.toDecimal(firstText(doc,
                "TotalTaxWithoutTax", "TotalAmountWithoutTax", "SumAmount", "合计金额"));
        BigDecimal tax = OcrFieldMapper.toDecimal(firstText(doc,
                "TotalTax", "TaxAmount", "合计税额"));
        BigDecimal total = OcrFieldMapper.toDecimal(firstText(doc,
                "TotalTaxedAmount", "TotalAmount", "价税合计"));
        if (total == null && net != null && tax != null) {
            total = net.add(tax);
        }
        r.setNetAmount(net);
        r.setTaxAmount(tax);
        r.setAmount(total);

        String rawText = buildXmlRawText(firstText(doc, "InvoiceType", "EInvoiceTitle", "Title", "票种"), r);
        r.setRawText(rawText);
        r.setInvoiceType(inferType(rawText));

        if (r.getInvoiceNo() == null || r.getInvoiceNo().isBlank()) {
            r.setOk(false);
            r.setErrorMsg("未从数电票 XML 解析到发票号码");
        }
        return r;
    }

    /** 关键节点拼接文本（留痕 + 票种推断用）：票面标题 + 号码/日期/购销方/价税合计 */
    private static String buildXmlRawText(String title, OcrResult r) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title).append('\n');
        }
        if (r.getInvoiceNo() != null) {
            sb.append("发票号码:").append(r.getInvoiceNo()).append('\n');
        }
        if (r.getInvoiceDate() != null) {
            sb.append("开票日期:").append(r.getInvoiceDate()).append('\n');
        }
        if (r.getBuyerName() != null) {
            sb.append("购买方:").append(r.getBuyerName()).append('\n');
        }
        if (r.getSellerName() != null) {
            sb.append("销售方:").append(r.getSellerName()).append('\n');
        }
        if (r.getAmount() != null) {
            sb.append("价税合计:").append(r.getAmount()).append('\n');
        }
        return sb.toString().trim();
    }

    // ===================== 中文标签正则解析（纯函数，PDF 与 OFD 文本兜底共用） =====================

    /**
     * 从票据纯文本按中文标签抽取票面字段（数电票版式文本顺序稳定，容错空格与全/半角冒号）。
     * 抽不到发票号码即 ok=false。金额支持 ¥/￥/千分位；日期归一 yyyy-MM-dd。
     */
    public static OcrResult parseInvoiceText(String text) {
        OcrResult r = new OcrResult();
        r.setRawText(text);
        r.setInvoiceCode(""); // 数电票无发票代码
        if (text == null || text.isBlank()) {
            r.setOk(false);
            r.setErrorMsg("票据文本为空");
            return r;
        }
        String no = group(P_INVOICE_NO, text);
        r.setInvoiceNo(no);

        String date = group(P_INVOICE_DATE, text);
        if (date != null) {
            r.setInvoiceDate(OcrFieldMapper.normalizeDate(date.replaceAll("\\s+", "")));
        }

        // 类型先判定：交通/定额/医疗等无"发票号码"字样，票号判定放宽
        String type = inferType(text);
        r.setInvoiceType(type);

        // 合计行：不含税 + 税额
        Matcher total = P_TOTAL_ROW.matcher(text);
        if (total.find()) {
            r.setNetAmount(OcrFieldMapper.toDecimal(total.group(1)));
            r.setTaxAmount(OcrFieldMapper.toDecimal(total.group(2)));
        }
        // 价税合计（小写）优先，缺失回退"价税合计"后首个金额
        String amount = group(P_AMOUNT_SMALL, text);
        if (amount == null) {
            amount = group(P_AMOUNT_TOTAL_FALLBACK, text);
        }
        if (amount == null) {
            amount = group(P_TOTAL_AMOUNT_FALLBACK, text);
        }
        if (amount == null) {
            amount = group(P_AMOUNT_FARE, text); // 交通/住宿票面无"合计/小写"列时按票价/客票款兜底
        }
        r.setAmount(OcrFieldMapper.toDecimal(amount));

        applyParties(r, text);
        r.setPrimaryItemName(extractPrimaryItemName(text));

        // 交通/定额/通行/停车/医疗等票据不强制"发票号码"：票号缺失不算失败，仅给提示
        if (no == null || no.isBlank()) {
            if (isNoNumberRequired(type)) {
                r.setOk(true);
            } else {
                r.setOk(false);
                r.setErrorMsg("未从票据文本中识别到发票号码");
            }
        }
        return r;
    }

    /** 购买方/销售方名称+税号：按"购买方"/"销售方"分区归属；分区不明显时按全文首现退化处理。
     *  新版数电票为"购销并排"版式（购 名称：A 销 名称：B），正文无连续"购买方/销售方"字样，
     *  而备注区常含"购买方地址/销售方地址"，故分区点必须后跟"名称/信"才有效，否则走退化分支。
     *  税号统一取全文前两个（购方在前、销方在后），避免并排版式下分区切分税号错位。 */
    private static void applyParties(OcrResult r, String text) {
        int buyerIdx = partyBlockIdx(text, "购买方", "购方", "购");
        int sellerIdx = partyBlockIdx(text, "销售方", "销售方信息", "卖方", "销方", "销");
        if (buyerIdx >= 0 && sellerIdx > buyerIdx) {
            String buyerRegion = text.substring(buyerIdx, sellerIdx);
            String sellerRegion = text.substring(sellerIdx);
            r.setBuyerName(usablePartyName(group(P_NAME, buyerRegion)));
            r.setSellerName(usablePartyName(group(P_NAME, sellerRegion)));
        } else {
            // 退化：全文首个可用名称记为购方，第二个可用记为销方（均经表头杂质过滤，跳过不可用项）
            Matcher name = P_NAME.matcher(text);
            String buyer = null;
            String seller = null;
            while (name.find()) {
                String n = usablePartyName(name.group(1).trim());
                if (n == null) {
                    continue;
                }
                if (buyer == null) {
                    buyer = n;
                } else {
                    seller = n;
                    break;
                }
            }
            r.setBuyerName(buyer);
            r.setSellerName(seller);
        }
        Matcher tax = P_TAX_NO.matcher(text);
        if (tax.find()) {
            r.setBuyerTaxNo(tax.group(1).trim());
            if (tax.find()) {
                r.setSellerTaxNo(tax.group(1).trim());
            }
        }
    }

    /**
     * 查找"购/销"分区块起点：标签后必须紧跟"名称"或"信"（信息），
     * 排除备注区"购买方地址/销售方地址/购方开户银行"等杂质标签；
     * 单字"购/销"（并排版式"购 名称："）仅当其后为"名称"时接受。
     */
    private static int partyBlockIdx(String text, String... labels) {
        int idx = -1;
        for (String k : labels) {
            int from = 0;
            while (true) {
                int i = text.indexOf(k, from);
                if (i < 0) {
                    break;
                }
                String tail = text.substring(Math.min(text.length(), i + k.length()))
                        .replaceFirst("^[\\s　\\r\\n]*", "");
                boolean blockStart = k.length() == 1
                        ? tail.startsWith("名称")
                        : (tail.startsWith("名称") || tail.startsWith("名") || tail.startsWith("信"));
                if (blockStart && (idx < 0 || i < idx)) {
                    idx = i;
                    break;
                }
                from = i + k.length();
            }
            if (idx >= 0) {
                break;
            }
        }
        return idx;
    }

    /** 名称字段可用性：剔除票面表头/合计杂质（避免"规格型号 单位 数量…"被误当作销方名），
     *  并截断 PDF 文本层同行混入的第二个"名称"标签及行尾残留单字（销/售/备/注 等） */
    private static String usablePartyName(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        // 截断到第二个"名称"标签前（购买/销售方同行时 group 会含"…名称：…"尾部）
        int second = t.indexOf("名称");
        if (second > 0) {
            t = t.substring(0, second).trim();
        }
        // 去除行尾残留的单字标签（销/售/备/注/购/买/方 等）
        t = t.replaceAll("[销销售备注购买方]{1,3}\\s*$", "").trim();
        // 老版式电子发票在购/销方名称后同行打印"校验码/防伪密文"（纯数字+半角符号，公司名称不可能含此类符号），
        // 截断尾部校验码，避免入库名称残留乱码（例：岳阳中石油昆仑燃气有限公司 9860<234/<*>1>0/3+036<68**8）
        t = t.replaceFirst("\\s*[0-9<>/+=*|&%^~!@#$\\\\-]{6,}$", "").trim();
        String low = t.replaceAll("\\s+", "");
        if (low.isEmpty() || low.contains("规格") || low.contains("型号") || low.contains("单位")
                || low.contains("数量") || low.contains("单价") || low.contains("金额")
                || low.contains("税率") || low.contains("税额") || low.contains("价税合计")
                || low.contains("货物或应税劳务") || low.contains("项目名称")
                || low.contains("合计") || low.contains("开票")) {
            return null;
        }
        return t;
    }

    // ===================== 票据类型（报销场景，编码与前端 constants/invoiceType.js 对齐） =====================
    public static final String T_VAT_SPECIAL = "VAT_SPECIAL";   // 增值税专用发票
    public static final String T_VAT_NORMAL = "VAT_NORMAL";     // 增值税普通发票
    public static final String T_ELECTRONIC = "ELECTRONIC";     // 电子发票/数电票
    public static final String T_RAIL_HIGH = "RAIL_HIGH";       // 高铁/动车
    public static final String T_TRAIN = "TRAIN";               // 火车（普速）
    public static final String T_AIR = "AIR";                   // 航空运输电子客票（行程单）
    public static final String T_TAXI = "TAXI";                 // 出租车客运发票
    public static final String T_RIDE_HALLING = "RIDE_HALLING"; // 网约车电子发票
    public static final String T_QUOTA = "QUOTA";               // 定额发票
    public static final String T_TOLL = "TOLL";                 // 通行费（含 ETC）
    public static final String T_PARKING = "PARKING";           // 停车费
    public static final String T_MEDICAL = "MEDICAL";           // 医疗收费票据
    public static final String T_OTHER = "OTHER";

    /**
     * 票面类型推断（按专一性从高到低依次判定，先于增值税通用分支判定交通/生活类票据）。
     * <ul>
     *   <li>医疗/定额/通行/停车/出租车/网约车有专有字样优先命中；</li>
     *   <li>航空（行程单/电子客票/航班）与铁路（高铁/动车/车站车次）判定先于增值税；</li>
     *   <li>其余按"专用 / 普通 / 电子(数电)"归入增值税三个编码。</li>
     * </ul>
     */
    static String inferType(String text) {
        if (text == null) {
            return T_OTHER;
        }
        // 去全部空白，避免 PDF 文本层把"电 子 发 票"等标签按字符拆行导致字面匹配失败
        String t = text.trim().replaceAll("\\s+", "");
        if (t.isEmpty()) {
            return T_OTHER;
        }
        // 医疗
        if (t.contains("医疗收费票据") || (t.contains("医疗") && t.contains("收费"))) return T_MEDICAL;
        // 定额
        if (t.contains("定额发票")) return T_QUOTA;
        // 通行费 / ETC
        if (t.contains("通行费") || (t.contains("ETC") && t.contains("收费"))) return T_TOLL;
        // 停车
        if (t.contains("停车") && t.contains("收费")) return T_PARKING;
        // 网约车（平台关键字）
        if (t.contains("网约车") || t.contains("滴滴出行") || t.contains("滴滴") || t.contains("曹操出行")
                || t.contains("首汽约车") || t.contains("如祺出行") || t.contains("花小猪")) return T_RIDE_HALLING;
        // 出租车
        if (t.contains("出租车") || t.contains("出租汽车") || t.contains("客运出租")) return T_TAXI;
        // 航空（行程单 / 电子客票 + 航班）
        if (t.contains("航空运输电子客票") || t.contains("行程单") || t.contains("登机牌")
                || (t.contains("电子客票") && t.contains("航班")) || t.contains("承运人")) return T_AIR;
        // 高铁 / 动车（铁路客运）
        if (t.contains("高铁") || t.contains("动车")) return T_RAIL_HIGH;
        // 火车（铁路普速）
        if (t.contains("中国铁路") || (t.contains("火车") && t.contains("车票")) || t.contains("铁路电子客票")
                || (t.contains("车站") && (t.contains("车次") || t.contains("候车")))) return T_TRAIN;
        // 增值税专用发票
        if (t.contains("专用发票") || t.contains("增值税专用")) return T_VAT_SPECIAL;
        // 含"增值税"字样即归增值税族（票面被拆行时也能兜底）：电子→数电,普通→普票
        if (t.contains("增值税")) {
            if (t.contains("普通") && !t.contains("电子")) return T_VAT_NORMAL;
            return t.contains("电子") || t.contains("数电") || t.contains("全电") ? T_ELECTRONIC : T_VAT_NORMAL;
        }
        if (t.contains("普通发票")) return T_VAT_NORMAL;
        if (t.contains("电子发票") || t.contains("数电票") || t.contains("全电发票")) return t.contains("专用") ? T_VAT_SPECIAL : T_ELECTRONIC;
        return T_OTHER;
    }

    /** 无增值税"发票号码"字样也不强制的类型：交通运输类与定额/通行/停车/医疗等票据 */
    private static boolean isNoNumberRequired(String type) {
        return T_RAIL_HIGH.equals(type) || T_TRAIN.equals(type) || T_AIR.equals(type)
                || T_TAXI.equals(type) || T_RIDE_HALLING.equals(type)
                || T_QUOTA.equals(type) || T_TOLL.equals(type) || T_PARKING.equals(type) || T_MEDICAL.equals(type);
    }

    // ===================== 工具 =====================

    private static String group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (m.find()) {
            String v = m.group(1);
            return v == null ? null : v.trim();
        }
        return null;
    }

    private static int firstIndex(String text, String... keys) {
        int idx = -1;
        for (String k : keys) {
            int i = text.indexOf(k);
            if (i >= 0 && (idx < 0 || i < idx)) {
                idx = i;
            }
        }
        return idx;
    }

    /** 取文件名小写扩展名（无点/无后缀返回空串） */
    public static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
