package com.fssc.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 坏账计提（rc_bad_debt，带 tenant_id，逻辑删除 del_flag）。
 * 按逾期账龄计提坏账准备并落库，同一应收单同一期仅计提一次。
 * 状态：PROVISIONED 已计提 / REVERSED 已转回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rc_bad_debt")
public class RcBadDebt extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 往来单位（crm_customer.id） */
    private Long customerId;
    /** 关联应收单id */
    private Long receivableId;
    /** 计提金额 */
    private BigDecimal amount;
    /** 计提日期 */
    private LocalDate provisionDate;
    /** 计提比例 */
    private BigDecimal provisionRate;
    /** 期间（yyyy-MM 或 yyyyMM） */
    private String period;
    /** PROVISIONED 已计提 / REVERSED 已转回 / WRITTEN_OFF 已核销 */
    private String status;
    /** 所属公司（多分子公司审批路由 V1.3.17） */
    private Long companyId;

    /** 坏账核销金额（蓝图流程22/模板11.4.2，≤已计提余额） */
    private BigDecimal writeoffAmount;
    /** 核销日期 */
    private LocalDate writeoffDate;
    /** 核销原因 */
    private String writeoffReason;
    /** 坏账认定依据编号（破产说明等附件） */
    private String basisNo;
    /** 坏账核销凭证号 */
    private String writeoffVoucherNo;

    /** R5-D-094 列表展示用——客户名称（来自 crm_customer，非 rc_bad_debt 库列） */
    @TableField(exist = false)
    private String customerName;
    /** R5-D-094 列表展示用——应收单号（来自 rc_receivable，非 rc_bad_debt 库列） */
    @TableField(exist = false)
    private String receivableBillNo;
}