package com.fssc.audit.util;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 关键字段校验和工具（P0-2 金额防篡改）。
 * 提交时对关键字段生成 SHA-256；审批/付款执行时校验，防止金额/收款账户被篡改。
 */
public final class ChecksumUtil {

    private static final String SEP = "|";

    private ChecksumUtil() {
    }

    /**
     * 生成校验和：字段按固定顺序拼接，null 归一化为空串。
     * 注意金额统一用 toPlainString（避免科学计数法），且用 stripTrailingZeros 归一化。
     */
    public static String sha256(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (Object f : fields) {
            if (f == null) {
                sb.append(SEP);
            } else if (f instanceof BigDecimal bd) {
                sb.append(bd.stripTrailingZeros().toPlainString()).append(SEP);
            } else if (f instanceof java.math.BigInteger bi) {
                sb.append(bi.toString()).append(SEP);
            } else {
                sb.append(f).append(SEP);
            }
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 常量时间比较，防时序侧信道 */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return a == b;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
