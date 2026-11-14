package com.fssc.tracing.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单据全链路溯源结果（单据→发票→凭证→付款→银行对账→档案）。
 * 联查、单据追溯。
 */
@Data
public class DocTraceChain {

    /** 源单据类型（EXP_BILL/PO_ORDER/INV_INVOICE/RC_RECEIVABLE等） */
    private String sourceDocType;
    /** 源单据ID */
    private Long sourceDocId;
    /** 源单据编号 */
    private String sourceDocNo;
    /** 源单据摘要 */
    private String sourceDocSummary;
    /** 源单据状态 */
    private String sourceDocStatus;
    /** 源单据金额 */
    private String sourceDocAmount;

    /** 关联发票列表 */
    private List<TraceNode> invoices;
    /** 关联凭证列表 */
    private List<TraceNode> vouchers;
    /** 关联付款批次列表 */
    private List<TraceNode> payBatches;
    /** 收款事实列表（资金流入侧，exp_bill_receipt） */
    private List<TraceNode> receipts;
    /** 银行对账记录 */
    private List<TraceNode> bankReconciles;
    /** 归档记录 */
    private List<TraceNode> archives;

    @Data
    public static class TraceNode {
        /** 节点类型：INVOICE/VOUCHER/PAY_BATCH/RECEIPT/BANK_RECON/ARCHIVE */
        private String nodeType;
        /** 节点ID */
        private Long id;
        /** 节点编号 */
        private String docNo;
        /** 状态 */
        private String status;
        /** 金额 */
        private String amount;
        /** 日期 */
        private String date;
        /** 摘要 */
        private String summary;
        /** 创建时间 */
        private LocalDateTime createTime;
    }
}
