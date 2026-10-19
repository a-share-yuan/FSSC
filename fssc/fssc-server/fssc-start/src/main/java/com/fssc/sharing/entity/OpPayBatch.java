package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出纳付款批次（op_pay_batch，带 tenant_id）。DRAFT → CONFIRMED → PAID。
 *
 * <p><b>D-DEC-114（12-25:78）②「批次与凭证零关联／①批次行没有自身审批流号」的落库口径说明：</b>
 * 本表 25 列里只有一个回溯列 {@code proc_inst_id}，其语义在 D-TRC-05 已定死＝<b>来源单据</b>的审批流
 * （{@code BillTraceChainController} 的「流程→批次」段、{@code VoucherService.generateTreasuryPayVoucher}
 * 的注释都依赖这一语义，改它＝把源单据的追溯链拆了，故不采纳「复用 proc_inst_id 存本批次流」的方案）。
 * 本批次自己的资金付款审批（TR_PAY）实例与凭证归属，<b>现阶段由代码读侧从权威表解析</b>
 * （{@code wf_instance(biz_type='TR_PAY', biz_id=id)} 与 {@code op_voucher(source_type='TREASURY_PAY', source_doc_id=id)}，
 * 见 {@code PayBatchService.fillDisplayFields}），即下面三个 {@code exist=false} 字段。
 *
 * <pre>
 * --  需人工裁决：以下 DDL 未执行、也不得由缺陷修复批次执行（注释态交付）。
 * -- 表结构落列后，把下面三行 @TableField(exist = false) 摘掉即可自动对上（MP 下划线转驼峰），
 * -- 并把 PayBatchService.confirm() 里捕获的 TR_PAY 实例号、PayBatchBizAction 收尾拿到的凭证号真正回写这两列。
 * -- ALTER TABLE op_pay_batch
 * --   ADD COLUMN pay_proc_inst_id VARCHAR(64) NULL COMMENT '本批次自身资金付款审批(TR_PAY)流程实例号（D-DEC-114；≠ proc_inst_id 那个源单据流）' AFTER proc_inst_id,
 * --   ADD COLUMN voucher_id       BIGINT      NULL COMMENT '批次出纳凭证ID（D-DEC-114②，op_voucher.id；P0-5 跨类型幂等跳过时指向单据级凭证）' AFTER pay_proc_inst_id,
 * --   ADD COLUMN voucher_no       VARCHAR(64) NULL COMMENT '批次出纳凭证号（冗余展示，D-DEC-114②）' AFTER voucher_id,
 * --   ADD KEY idx_pay_proc_inst_id (pay_proc_inst_id),
 * --   ADD KEY idx_voucher_id (voucher_id);
 * --
 * -- 存量回灌（与建列同一裁决一起做，否则老批次仍是空列、列表口径新旧两套）：
 * -- UPDATE op_pay_batch b JOIN wf_instance w ON w.biz_type='TR_PAY' AND w.biz_id=b.id
 * --    SET b.pay_proc_inst_id = w.proc_inst_id WHERE b.pay_proc_inst_id IS NULL;
 * -- UPDATE op_pay_batch b JOIN op_voucher v ON v.source_type='TREASURY_PAY' AND v.source_doc_id=b.id AND v.del_flag=0
 * --    SET b.voucher_id=v.id, b.voucher_no=v.voucher_no WHERE b.voucher_id IS NULL;
 * -- P0-5 跨类型跳过（批次不出证、由单据级 EXPENSE_PAY 凭证承载）的那批批次，voucher_id 只能经
 * -- op_pay_batch_detail → exp_bill_payment.bill_id → op_voucher.bill_id 推出，多单可能多张凭证⇒一列存不下，
 * -- 该子集是否落列（改存关联表 op_pay_batch_voucher）同属本项裁决，宁缺毋滥：取不到就留 NULL，
 * -- 不给批次写一个"猜出来的"凭证号（同 D-DEC-280／本类 proc_inst_id「多源合并留 NULL」口径）。
 * -- </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_pay_batch")
public class OpPayBatch extends TenantAuditBase {

    private String batchNo;
    private Long companyId;
    /** 出账账户（md_bank_account） */
    private Long bankAccountId;
    /** BANK银企直联/IMPORT导入 */
    private String payMethod;
    private BigDecimal totalAmount;
    private Integer detailCount;
    private String currencyCode;
    /** DRAFT/CONFIRMED/APPROVED/PAID/FAILED/CLOSED/ARCHIVED */
    private String status;
    private Long submitUserId;
    private LocalDateTime submitTime;
    private LocalDateTime finishTime;
    /** 银行响应 */
    private String bankResp;
    /** 支付失败原因（FAILED 后可重试） */
    private String failReason;
    private LocalDateTime failTime;
    private String remark;
    /** P0-4 溯源：来源单据类型 EXP_BILL_PAYMENT付款记录/AUTO_GROUP自动组批 */
    private String sourceDocType;
    /** P0-4 溯源：来源单据ID */
    private Long sourceDocId;
    /** D-TRC-05：来源单据的审批流程实例ID；跨单合并批次留 NULL（多实例不可归一） */
    private String procInstId;

    /** D-DEC-252 展示：来源单据状态（transient，列表富化；批次内多单状态不一致时为 MIXED） */
    @TableField(exist = false)
    private String billStatus;
    /** D-DEC-252 展示：关联凭证号（transient，多张以「、」连接） */
    @TableField(exist = false)
    private String voucherNo;
    /** D-DEC-252 展示：关联凭证记账状态（transient，DRAFT/POSTED；多张时任一未过账即 DRAFT） */
    @TableField(exist = false)
    private String voucherStatus;
    /**
     * D-DEC-114①：本批次<b>自身</b>的资金付款审批（TR_PAY）流程实例号（transient，读侧富化）。
     * 与 {@link #procInstId} 不是一回事——后者按 D-TRC-05 是<b>来源单据</b>的审批流（dc5ea295 那一类），
     * 缺陷实测「从批次点追溯直达另一条流程」就是拿它当批次流用造成的。
     * 落库需 DDL（见类注释），现由 {@code PayBatchService.fillDisplayFields} 从
     * {@code wf_instance(biz_type='TR_PAY', biz_id=id)} 取最新一条，与 {@code Pay.vue} 已声明的
     * {@code biz-type="TR_PAY"} 追溯入口同口径，前端可直接把它当追溯主键用。
     */
    @TableField(exist = false)
    private String payProcInstId;
    /** D-DEC-114① 展示：本批次 TR_PAY 审批流状态（transient，RUNNING/APPROVED/REJECTED…；未发起流则为空） */
    @TableField(exist = false)
    private String payFlowStatus;
    /**
     * R5-D-079③ 展示：账证不符异常标记（transient，读侧富化；正常行 null）。
     * {@code VOID_WITH_POSTED_VOUCHER}＝批次已作废（VOID）却仍挂已记账（POSTED）活凭证；
     * {@code PAID_WITHOUT_VOUCHER}＝批次已付款（PAID）却 0 张活凭证。两组合在列表以红色标记呈现，
     * 并在 {@code PayBatchService.execute} 入口同判据 fail-closed 禁止再次付款。
     */
    @TableField(exist = false)
    private String voucherAnomaly;
}
