package com.fssc.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 收款核销单（rc_receipt，带 tenant_id，逻辑删除 del_flag）。
 * 一笔收款可核销多个应收单（receivableIds 逗号分隔），按先进先出(FIFO)冲减余额。
 * 状态：WRITTEN_OFF 已核销。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rc_receipt")
public class RcReceipt extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 单据号 */
    private String billNo;
    /** 核销的多个应收单id，逗号分隔 */
    private String receivableIds;
    /** 往来单位（crm_customer.id） */
    private Long customerId;
    /** 收款日期 */
    private LocalDate receiptDate;
    /** 收款方式：银行/现金/票据 */
    private String payMethod;
    /** 收款金额 */
    private BigDecimal amount;
    /** 经办人（sys_user.id） */
    private Long operatorId;
    /** WRITTEN_OFF 已核销 */
    private String status;
    /** 勾对的银行流水号（收款方向未达账项判定用） */
    private String bankSerial;
    /** 是否与银行流水勾对：0未勾对(企业已收银行未收) 1已勾对 */
    private Integer bankMatched;

    /** 核销明细（#12，仅入参/回显，非表字段；落 rc_receipt_line） */
    @TableField(exist = false)
    private List<RcReceiptLine> lines;
}