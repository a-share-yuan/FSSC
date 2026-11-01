// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OcrLocalParsers 单测：纯函数 + 测试内构造最小 OFD 字节流，不依赖外部文件。
 * 覆盖：中文标签正则解析（数电票文本）、OFD 内嵌 TTSRCdata.xml 结构解析、OFD TextCode 文本兜底、
 * PDF 无文本层降级、格式分派判定。
 */
class OcrLocalParsersTest {

    private static final String SHUIAN_TEXT = String.join("\n",
            "电子发票（增值税专用发票）",
            "发票号码：24442000000012345678",
            "开票日期：2024年08月10日",
            "购买方名称：某某买方科技有限公司",
            "统一社会信用代码/纳税人识别号：91310000MA1AAAAA1A",
            "销售方名称：某某卖方商贸有限责任公司",
            "统一社会信用代码/纳税人识别号：91440300MA5BBBBB2B",
            "项目名称 规格型号 单位 数量 单价 金额 税率/征收率 税额",
            "*信息技术服务*技术服务费 1000.00 6% 60.00",
            "合计 ¥1000.00 ¥60.00",
            "价税合计（大写） 壹仟零陆拾元整 （小写）¥1060.00",
            "开票人：张三");

    @Test
    void 中文标签正则解析数电票_全字段命中() {
        OcrResult r = OcrLocalParsers.parseInvoiceText(SHUIAN_TEXT);
        assertTrue(r.isOk());
        assertEquals("24442000000012345678", r.getInvoiceNo());
        assertEquals("2024-08-10", r.getInvoiceDate());          // 中文日期归一 yyyy-MM-dd
        assertEquals("", r.getInvoiceCode());                     // 数电票无发票代码
        assertEquals(0, r.getNetAmount().compareTo(new BigDecimal("1000.00"))); // 合计行前段=不含税
        assertEquals(0, r.getTaxAmount().compareTo(new BigDecimal("60.00")));    // 合计行后段=税额
        assertEquals(0, r.getAmount().compareTo(new BigDecimal("1060.00")));     // 价税合计（小写）
        assertEquals("某某买方科技有限公司", r.getBuyerName());
        assertEquals("91310000MA1AAAAA1A", r.getBuyerTaxNo());
        assertEquals("某某卖方商贸有限责任公司", r.getSellerName());
        assertEquals("91440300MA5BBBBB2B", r.getSellerTaxNo());
        assertEquals("VAT_SPECIAL", r.getInvoiceType());          // 含"专用发票"字样
        // #15 自洽：不含税 + 税额 = 价税合计
        assertEquals(0, r.getNetAmount().add(r.getTaxAmount()).compareTo(r.getAmount()));
    }

    @Test
    void 货名提取_星号标签兜底与杂质过滤() {
        // *税收分类简称*具体名 → 取具体名（票面「货物或应税劳务、服务名称」列的具体名才是货名，
        // 星号内是税收分类简称，仅在具体名不可用时兜底；见 OcrLocalParsers.extractPrimaryItemName）
        assertEquals("住宿费", OcrLocalParsers.extractPrimaryItemName(
                "*住宿服务*住宿费 1000.00\n合计 ¥1000.00"));
        // 表头杂质（规格/数量/金额）被过滤，命中星号后的具体名
        assertEquals("技术服务费", OcrLocalParsers.extractPrimaryItemName(
                "项目名称 规格型号 单位 数量 单价 金额 税率 税额\n*信息技术服务*技术服务费 1000.00 6% 60.00"));
        // 无货名 → null
        assertNull(OcrLocalParsers.extractPrimaryItemName("发票号码：12345678 价税合计 ¥10.00"));
    }

    @Test
    void 中文标签正则解析_普通发票与无号码降级失败() {
        // 数电票面只把「专用」换成「普通」并不会摘掉电子特征：首行仍是「电子发票（增值税普通发票）」、
        // 票号仍是 20 位号段 ⇒ 归类 ELECTRONIC。非电子普票的 VAT_NORMAL 路径由 OFD 无票号夹具覆盖。
        OcrResult normal = OcrLocalParsers.parseInvoiceText(
                SHUIAN_TEXT.replace("增值税专用发票", "增值税普通发票"));
        assertEquals("ELECTRONIC", normal.getInvoiceType());
        // 抽不到发票号码 → ok=false（供 mock 降级 / http 置 FAILED）
        OcrResult noNo = OcrLocalParsers.parseInvoiceText("这是一张没有票号信息的收据\n合计 ¥10.00");
        assertFalse(noNo.isOk());
        assertNull(noNo.getInvoiceNo());
    }

    @Test
    void OFD内嵌TTSRCdataXML_结构映射解析() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<EInvoice>\n"
                + "  <EInvoiceTitle>电子发票（增值税专用发票）</EInvoiceTitle>\n"
                + "  <InvoiceNumber>24442000000099999999</InvoiceNumber>\n"
                + "  <IssueTime>2024-09-01</IssueTime>\n"
                + "  <BuyerInformation><BuyerName>某某买方甲</BuyerName>"
                + "<BuyerIdNum>91310000AAAAAAAAAA</BuyerIdNum></BuyerInformation>\n"
                + "  <SellerInformation><SellerName>某某卖方乙</SellerName>"
                + "<SellerIdNum>91440300BBBBBBBBBB</SellerIdNum></SellerInformation>\n"
                + "  <TotalTaxWithoutTax>500.00</TotalTaxWithoutTax>\n"
                + "  <TotalTax>30.00</TotalTax>\n"
                + "  <TotalTaxedAmount>530.00</TotalTaxedAmount>\n"
                + "</EInvoice>";
        byte[] ofd = zipOf("Doc_0/Attachs/TTSRCdata.xml", xml);

        OcrResult r = OcrLocalParsers.parse(ofd, "dzfp.ofd");
        assertTrue(r.isOk());
        assertEquals("24442000000099999999", r.getInvoiceNo());
        assertEquals("2024-09-01", r.getInvoiceDate());
        assertEquals(0, r.getNetAmount().compareTo(new BigDecimal("500.00")));
        assertEquals(0, r.getTaxAmount().compareTo(new BigDecimal("30.00")));
        assertEquals(0, r.getAmount().compareTo(new BigDecimal("530.00")));
        assertEquals("某某买方甲", r.getBuyerName());
        assertEquals("91310000AAAAAAAAAA", r.getBuyerTaxNo());
        assertEquals("某某卖方乙", r.getSellerName());
        assertEquals("91440300BBBBBBBBBB", r.getSellerTaxNo());
        assertEquals("VAT_SPECIAL", r.getInvoiceType());
        assertEquals(0, r.getNetAmount().add(r.getTaxAmount()).compareTo(r.getAmount()));
    }

    @Test
    void OFD无内嵌XML_ContentTextCode文本兜底解析() throws Exception {
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Page>\n"
                + "  <TextCode X=\"10\" Y=\"10\">发票号码：24442000000055555555</TextCode>\n"
                + "  <TextCode>开票日期：2024年07月01日</TextCode>\n"
                + "  <TextCode>购买方名称：某某丙公司</TextCode>\n"
                + "  <TextCode>统一社会信用代码/纳税人识别号：91310000CCCCCCCCCC</TextCode>\n"
                + "  <TextCode>销售方名称：某某丁公司</TextCode>\n"
                + "  <TextCode>统一社会信用代码/纳税人识别号：91440300DDDDDDDDDD</TextCode>\n"
                + "  <TextCode>合计 ¥200.00 ¥12.00</TextCode>\n"
                + "  <TextCode>价税合计（大写）贰佰壹拾贰圆整（小写）¥212.00</TextCode>\n"
                + "  <TextCode>电子发票（普通发票）</TextCode>\n"
                + "</Page>";
        byte[] ofd = zipOf("Doc_0/Pages/Page_0/Content.xml", content);

        OcrResult r = OcrLocalParsers.parse(ofd, "ofd-noinvoice.ofd");
        assertTrue(r.isOk());
        assertEquals("24442000000055555555", r.getInvoiceNo());
        assertEquals("2024-07-01", r.getInvoiceDate());
        assertEquals("某某丙公司", r.getBuyerName());
        assertEquals("某某丁公司", r.getSellerName());
        assertEquals(0, r.getNetAmount().compareTo(new BigDecimal("200.00")));
        assertEquals(0, r.getTaxAmount().compareTo(new BigDecimal("12.00")));
        assertEquals(0, r.getAmount().compareTo(new BigDecimal("212.00")));
        assertEquals("VAT_NORMAL", r.getInvoiceType());
    }

    @Test
    void PDF无文本层_返回失败不抛穿() {
        // 非法/无文本层字节：PDFBox 抽取失败 → parse 返回 ok=false（供 http 置 FAILED / mock 降级）
        byte[] notPdf = "%PDF-1.4 broken-not-a-real-pdf".getBytes(StandardCharsets.UTF_8);
        OcrResult r = OcrLocalParsers.parse(notPdf, "scan.pdf");
        assertFalse(r.isOk());
        assertTrue(r.getErrorMsg().contains("PDF"));
    }

    @Test
    void 格式分派判定与空文件() {
        assertTrue(OcrLocalParsers.isLocalFormat("a.b.c.pdf"));
        assertTrue(OcrLocalParsers.isLocalFormat("发票.OFD"));   // 大小写无关
        assertTrue(OcrLocalParsers.isLocalFormat("dzfp.xml"));   // 数电票原始 XML 走本地解析
        assertFalse(OcrLocalParsers.isLocalFormat("photo.jpg"));
        assertFalse(OcrLocalParsers.isLocalFormat("photo.png"));
        assertFalse(OcrLocalParsers.isLocalFormat("noext"));
        assertFalse(OcrLocalParsers.parse(null, "x.pdf").isOk());
        assertFalse(OcrLocalParsers.parse(new byte[0], "x.ofd").isOk());
    }

    @Test
    void 数电票XML标准样例_全字段解析与类型推断() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<EInvoice>\n"
                + "  <EInvoiceTitle>电子发票（增值税专用发票）</EInvoiceTitle>\n"
                + "  <InvoiceNumber>24442000000012345678</InvoiceNumber>\n"
                + "  <IssueTime>2024年08月10日</IssueTime>\n"
                + "  <BuyerName>某某买方科技有限公司</BuyerName>\n"
                + "  <BuyerTaxNo>91310000MA1AAAAA1A</BuyerTaxNo>\n"
                + "  <SellerName>某某卖方商贸有限责任公司</SellerName>\n"
                + "  <SellerTaxNo>91440300MA5BBBBB2B</SellerTaxNo>\n"
                + "  <TotalTaxWithoutTax>1000.00</TotalTaxWithoutTax>\n"
                + "  <TaxAmount>60.00</TaxAmount>\n"
                + "  <TotalTaxedAmount>1060.00</TotalTaxedAmount>\n"
                + "</EInvoice>";
        OcrResult r = OcrLocalParsers.parse(xml.getBytes(StandardCharsets.UTF_8), "dzfp.xml");
        assertTrue(r.isOk());
        assertEquals("", r.getInvoiceCode());                     // 数电票无发票代码
        assertEquals("24442000000012345678", r.getInvoiceNo());
        assertEquals("2024-08-10", r.getInvoiceDate());           // 中文日期归一 yyyy-MM-dd
        assertEquals("某某买方科技有限公司", r.getBuyerName());
        assertEquals("91310000MA1AAAAA1A", r.getBuyerTaxNo());
        assertEquals("某某卖方商贸有限责任公司", r.getSellerName());
        assertEquals("91440300MA5BBBBB2B", r.getSellerTaxNo());
        assertEquals(0, r.getNetAmount().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, r.getTaxAmount().compareTo(new BigDecimal("60.00")));
        assertEquals(0, r.getAmount().compareTo(new BigDecimal("1060.00")));
        assertEquals("VAT_SPECIAL", r.getInvoiceType());          // 含"专用发票"字样
        // #15 自洽：不含税 + 税额 = 价税合计
        assertEquals(0, r.getNetAmount().add(r.getTaxAmount()).compareTo(r.getAmount()));
    }

    @Test
    void 数电票XML含DOCTYPE外部实体_防XXE解析失败且不读取外部文件() throws Exception {
        java.nio.file.Path secret = java.nio.file.Files.createTempFile("fssc-xxe", ".txt");
        try {
            java.nio.file.Files.writeString(secret, "SECRET-24442000000099999999");
            // 恶意数电票 XML：用 DOCTYPE + SYSTEM 外部实体把外部文件内容注入发票号码
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE EInvoice [<!ENTITY ext SYSTEM \"" + secret.toUri() + "\">]>\n"
                    + "<EInvoice><InvoiceNumber>&ext;</InvoiceNumber></EInvoice>";
            OcrResult r = OcrLocalParsers.parse(xml.getBytes(StandardCharsets.UTF_8), "evil.xml");
            // parseXml 已 disallow-doctype-decl：遇到 <!DOCTYPE 即致命错误 → 显性失败（OcrService 置 FAILED）
            assertFalse(r.isOk());
            assertNull(r.getInvoiceNo());                          // 实体未展开，号码未被外部文件污染
            assertTrue(r.getRawText() == null || !r.getRawText().contains("SECRET"));
        } finally {
            java.nio.file.Files.deleteIfExists(secret);
        }
    }

    @Test
    void 数电票XML节点名同义词容错_InvoiceNo与InvoiceNumber均可取号() {
        OcrResult byFull = OcrLocalParsers.parse(
                ("<EInvoice><InvoiceNumber>24442000000077777777</InvoiceNumber></EInvoice>")
                        .getBytes(StandardCharsets.UTF_8), "a.xml");
        OcrResult byAlias = OcrLocalParsers.parse(
                ("<EInvoice><InvoiceNo>24442000000088888888</InvoiceNo></EInvoice>")
                        .getBytes(StandardCharsets.UTF_8), "b.xml");
        assertTrue(byFull.isOk());
        assertEquals("24442000000077777777", byFull.getInvoiceNo());
        assertTrue(byAlias.isOk());
        assertEquals("24442000000088888888", byAlias.getInvoiceNo());
    }

    @Test
    void 多类票据类型推断与交通类不强制票号() {
        // 高铁/动车：含"动车" → RAIL_HIGH；无"发票号码"，票号缺失仍 ok
        OcrResult rail = OcrLocalParsers.parseInvoiceText(
                "中国铁路上海局 高铁动车组车票 乘车日期：2024年09月11日 G1234次 票价￥553.5");
        assertEquals("RAIL_HIGH", rail.getInvoiceType());
        assertTrue(rail.isOk());

        // 火车（普速）：铁路电子客票 → TRAIN
        OcrResult train = OcrLocalParsers.parseInvoiceText(
                "中国铁路 铁路电子客票 报销凭证 乘车日期：2024年09月11日 车次 K102 ￥18.5");
        assertEquals("TRAIN", train.getInvoiceType());
        assertTrue(train.isOk());

        // 航空：行程单/电子客票+航班 → AIR；无增值税发票号仍 ok
        OcrResult air = OcrLocalParsers.parseInvoiceText(
                "航空运输电子客票（行程单）航班号 MU5123 客票号781-1234567890 承运人 票价￥1280.00");
        assertEquals("AIR", air.getInvoiceType());
        assertTrue(air.isOk());

        // 出租车
        OcrResult taxi = OcrLocalParsers.parseInvoiceText(
                "出租车客运发票 发票号码 3100123456789 起止 金额 ¥38.00 发票代码 00001");
        assertEquals("TAXI", taxi.getInvoiceType());

        // 网约车（滴滴）+行程单 电子发票
        OcrResult ride = OcrLocalParsers.parseInvoiceText("滴滴出行 网约车 电子发票 行程单 金额 ¥25.80");
        assertEquals("RIDE_HALLING", ride.getInvoiceType());

        // 定额发票
        OcrResult quota = OcrLocalParsers.parseInvoiceText("定额发票（壹佰圆整）100");
        assertEquals("QUOTA", quota.getInvoiceType());

        // 通行费 / ETC
        OcrResult toll = OcrLocalParsers.parseInvoiceText("ETC高速公路通行费发票 金额 ¥120.50");
        assertEquals("TOLL", toll.getInvoiceType());

        // 医疗收费票据
        OcrResult med = OcrLocalParsers.parseInvoiceText("医疗收费票据（门诊） 金额 ¥350.00");
        assertEquals("MEDICAL", med.getInvoiceType());
    }
    private static byte[] zipOf(String entryName, String content) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
