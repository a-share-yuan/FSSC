// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 记账凭证（op_voucher，带 tenant_id）。审核完成生成，DRAFT → POSTED。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_voucher")
public class OpVoucher extends TenantAuditBase {

    private String voucherNo;
    /** JOURNAL记账凭证 */
    private String voucherWord;
    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    private LocalDate voucherDate;
    private Integer attachCount;
    /** 来源单据ID */
    private Long billId;
    private String sourceType;
    private String summary;
    private BigDecimal debitTotal;
    private BigDecimal creditTotal;
    /** 1 借贷平衡 */
    private Integer isBalanced;
    /** DRAFT/POSTED */
    private String status;
    private Long postUserId;
    private LocalDateTime postTime;
    private String remark;
    /** 溯源：来源单据类型（标准化字段，与sourceType语义一致） */
    private String sourceDocType;
    /** 溯源：来源单据ID（标准化字段，与billId语义一致） */
    private Long sourceDocId;
    /** 展示：来源单据号（transient，查询时冗余，便于列表核对） */
    @TableField(exist = false)
    private String sourceBillNo;
    /** 展示：来源单据状态（transient，查询时冗余，便于列表核对） */
    @TableField(exist = false)
    private String sourceBillStatus;
    /** 展示：制单人姓名（transient；create_by 存的是 sys_user.id，列表不能直接甩数字） */
    @TableField(exist = false)
    private String createByName;
    /**
     * X-49 乙 展示：付款环节凭证"资金指令未下发"标记（transient，只读补充，零写库）。
     * <p>true＝本凭证是付款环节、账面已记账（POSTED）但资金证据说款还没付出；
     * false＝资金证据已实付；null＝不是付款环节／无资金证据可判（界面不注记）。
     * <p>由 {@code VoucherAutoPostService.fillPayLegUnsettledFlag} 在凭证台账读侧打标，
     * <b>不改 {@code status}</b>——历史已记账凭证保持不动账（红字冲销／回退草稿须财务另签）。
     */
    @TableField(exist = false)
    private Boolean payLegUnsettled;
    /**
     * 展示：该凭证在 op_gl_entry 的落账分录行数（transient，列表接口一次性聚合带回，零写库）。
     * <p>>0＝已落账、0＝未落账，由 {@code GlService.countGlEntriesByVoucherIds} 单条 GROUP BY 聚合
     * 给出整页读数（取代前端逐张 {@code /op/gl/posted} 的 N+1 串行探测，杜绝"探测失败被降级成未落账"）；
     * null＝本次列表没带回聚合——前端须显式标「校验失败」并可重试，不得降级显示成「未落账」。
     */
    @TableField(exist = false)
    private Integer glEntryCount;
    /** 来源单据的审批流程实例ID，使凭证能反查"是哪次审批产生的"；非费用域来源暂留 NULL */
    private String procInstId;
}
