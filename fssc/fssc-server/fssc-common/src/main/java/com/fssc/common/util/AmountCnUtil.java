// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.util;

import java.math.BigDecimal;

/**
 * 中文大写金额转换工具（用于报销单/借款单金额大写字段 amount_cn，打印/OA 必需）。
 * 标准金融大写：零壹贰叁肆伍陆柒捌玖 + 拾佰仟万亿 + 元角分整。
 */
public final class AmountCnUtil {

    private static final String[] CN_NUM = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_SECTION = {"", "万", "亿", "万亿"};
    private static final String[] CN_DIGIT = {"", "拾", "佰", "仟"};

    private AmountCnUtil() {
    }

    /**
     * 将金额转换为中文大写，如 {@code 1234.56} → {@code 壹仟贰佰叁拾肆元伍角陆分}。
     *
     * @param amount 金额，null 或 ≤0 时返回 null（不生成大写，由业务侧决定是否拦截）
     * @return 中文大写金额；金额为空或非正数返回 null
     */
    public static String toCn(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // 精确到分（四舍五入），仅截取整数部分与角分
        BigDecimal cents = amount.setScale(2, BigDecimal.ROUND_HALF_UP);
        long intPart = cents.longValue();
        int remainingCents = cents.subtract(BigDecimal.valueOf(intPart))
                .multiply(new BigDecimal("100")).intValue();
        int jiao = remainingCents / 10;
        int fen = remainingCents % 10;

        StringBuilder sb = new StringBuilder();
        sb.append(convertInt(intPart));
        sb.append("元");
        if (jiao == 0 && fen == 0) {
            sb.append("整");
            return sb.toString();
        }
        if (jiao > 0) {
            sb.append(CN_NUM[jiao]).append("角");
        }
        if (fen > 0) {
            if (jiao == 0) {
                sb.append("零");
            }
            sb.append(CN_NUM[fen]).append("分");
        }
        return sb.toString();
    }

    /** 整数部分转换（支持到万亿），返回不含“元”。自高位向低位拼接，节内复用 convertSection。 */
    private static StringBuilder convertInt(long value) {
        StringBuilder sb = new StringBuilder();
        if (value == 0) {
            return sb.append(CN_NUM[0]);
        }
        int[] secs = new int[4];
        int n = 0;
        while (value > 0 && n < secs.length) {
            secs[n++] = (int) (value % 10000);
            value /= 10000;
        }
        for (int i = n - 1; i >= 0; i--) {
            int s = secs[i];
            if (s == 0) {
                // 整节为零：若低位仍有非零节，补一个“零”（连续零节合并）
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '零' && hasNonZeroBelow(secs, i)) {
                    sb.append('零');
                }
                continue;
            }
            if (sb.length() > 0 && s < 1000 && sb.charAt(sb.length() - 1) != '零') {
                sb.append('零');
            }
            sb.append(convertSection(s)).append(CN_SECTION[i]);
        }
        return sb;
    }

    private static boolean hasNonZeroBelow(int[] secs, int aboveIndex) {
        for (int j = 0; j < aboveIndex; j++) {
            if (secs[j] != 0) {
                return true;
            }
        }
        return false;
    }

    /** 四位数段转换（如 2304 → 贰仟叁佰零肆）。 */
    private static StringBuilder convertSection(int section) {
        StringBuilder sb = new StringBuilder();
        boolean zero = false;
        int pos = 0;
        while (section > 0) {
            int d = section % 10;
            if (d == 0) {
                if (sb.length() > 0 && sb.charAt(0) != '零') {
                    zero = true;
                }
            } else {
                if (zero) {
                    sb.insert(0, "零");
                    zero = false;
                }
                sb.insert(0, CN_DIGIT[pos]);
                sb.insert(0, CN_NUM[d]);
            }
            pos++;
            section /= 10;
        }
        return sb;
    }
}