// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.channel;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 临时诊断：解析指定目录下所有发票文件，输出解析结果与关键文本 */
public class DiagPdf {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "测试发票";
        Path d = Paths.get(dir);
        if (Files.isRegularFile(d)) {
            runOne(d);
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(d)) {
            for (Path p : ds) {
                String fn = p.getFileName().toString().toLowerCase();
                if (fn.endsWith(".pdf") || fn.endsWith(".ofd") || fn.endsWith(".xml") || fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png")) {
                    try {
                        runOne(p);
                    } catch (Exception e) {
                        System.out.println("===== " + p.getFileName() + " ==== FAIL " + e.getMessage());
                    }
                }
            }
        }
    }

    static void runOne(Path p) throws Exception {
        byte[] bytes = Files.readAllBytes(p);
        String fn = p.getFileName().toString();
        OcrResult r = OcrLocalParsers.parse(bytes, fn);
        System.out.println("===== " + fn + " =====");
        System.out.println("type=" + r.getInvoiceType() + " ok=" + r.isOk() + " scanned=" + r.isScanned());
        System.out.println("  no=" + r.getInvoiceNo() + " date=" + r.getInvoiceDate());
        System.out.println("  amount=" + r.getAmount() + " net=" + r.getNetAmount() + " tax=" + r.getTaxAmount());
        System.out.println("  seller=" + r.getSellerName() + " | buyer=" + r.getBuyerName());
        System.out.println("  stax=" + r.getSellerTaxNo() + " | btax=" + r.getBuyerTaxNo());
        System.out.println("  item=" + r.getPrimaryItemName() + " | err=" + r.getErrorMsg());
        String raw = r.getRawText();
        if (raw != null) {
            String compact = raw.replaceAll("\\s+", "");
            int iSeller = compact.lastIndexOf("销售方");
            int iBuyer = compact.indexOf("购买方");
            if (iSeller >= 0) {
                int s = Math.max(0, iSeller - 10);
                int e = Math.min(compact.length(), iSeller + 70);
                System.out.println("  [销方区] " + compact.substring(s, e));
            }
            if (iBuyer >= 0 && iSeller < 0) {
                int s = Math.max(0, iBuyer);
                int e = Math.min(compact.length(), iBuyer + 70);
                System.out.println("  [购方区] " + compact.substring(s, e));
            }
            if (r.getSellerName() == null || r.getSellerName().isEmpty()) {
                System.out.println("  [RAW全文] " + compact.substring(0, Math.min(compact.length(), 900)));
                try {
                    Files.write(Paths.get("_diag_raw.txt"),
                            ("===== " + fn + " =====\n" + raw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception ignore) { }
            }
            try {
                Files.write(Paths.get("_diag_last.txt"),
                        ("===== " + fn + " =====\n" + raw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignore) { }
        }
    }
}
