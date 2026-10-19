package com.fssc.connector.api;

/**
 * 连接能力域（形态 B：{@code com.fssc.connector} 对外暴露的能力分类）。
 *
 * <p>它把 {@code ext_interface_config.category} 这类"厂商/通道级"标识，收敛成业务与治理层可判定的
 * 能力语义，并承载一条关键治理规则：<b>变更类能力有外部副作用，无幂等键时禁止自动重试；
 * 只读类能力可安全重试</b>。该分类与历史 {@code DefaultIntegrationGateway.READ_ONLY_CATEGORIES}
 * 保持一致（AI_MODEL / OCR / INVOICE_VERIFY / INVOICE_CERTIFY 为只读）。</p>
 */
public enum ChannelType {

    /** AI 大模型（只读） */
    AI_MODEL(false),
    /** OCR 影像识别（只读） */
    OCR(false),
    /** 发票验真（只读） */
    INVOICE_VERIFY(false),
    /** 发票认证（只读） */
    INVOICE_CERTIFY(false),
    /** 银企直联 / 付款（变更：产生资金动作） */
    BANK_PAYMENT(true),
    /** 税务 / 金税开票（变更） */
    TAX_INVOICE(true),
    /** ERP 记账 / 回写（变更） */
    ERP(true),
    /** 凭证推送（变更） */
    VOUCHER_PUSH(true),
    /** 其它未归类（按最保守处理：视为变更类，默认不自动重试） */
    OTHER(true);

    private final boolean mutating;

    ChannelType(boolean mutating) {
        this.mutating = mutating;
    }

    /** 是否为变更类（有外部副作用）→ 无幂等键时禁止自动重试。 */
    public boolean isMutating() {
        return mutating;
    }

    /** 是否只读（无外部副作用，可安全重试）。 */
    public boolean isReadOnly() {
        return !mutating;
    }

    /**
     * 由通道分类标识（{@code ext_interface_config.category}）推导能力域。
     * 无法归类的一律归入 {@link #OTHER}（变更类，最保守），避免误判为可重试。
     */
    public static ChannelType of(String category) {
        if (category == null || category.isBlank()) {
            return OTHER;
        }
        String c = category.trim().toUpperCase();
        switch (c) {
            case "AI_MODEL":
                return AI_MODEL;
            case "OCR":
                return OCR;
            case "INVOICE_VERIFY":
                return INVOICE_VERIFY;
            case "INVOICE_CERTIFY":
                return INVOICE_CERTIFY;
            default:
                break;
        }
        if (c.startsWith("INVOICE_VERIFY")) {
            return INVOICE_VERIFY;
        }
        if (c.startsWith("INVOICE_CERTIFY")) {
            return INVOICE_CERTIFY;
        }
        if (c.startsWith("GOLDEN_TAX") || c.contains("TAX")) {
            return TAX_INVOICE;
        }
        if (c.contains("BANK")) {
            return BANK_PAYMENT;
        }
        if (c.contains("VOUCHER")) {
            return VOUCHER_PUSH;
        }
        if (c.startsWith("EBS") || c.startsWith("SAP") || c.startsWith("YONYOU")
                || c.startsWith("KINGDEE") || c.equals("ERP")
                || c.endsWith("_AP") || c.endsWith("_AP_BLUE") || c.endsWith("_ORDER_GET")) {
            return ERP;
        }
        return OTHER;
    }
}
