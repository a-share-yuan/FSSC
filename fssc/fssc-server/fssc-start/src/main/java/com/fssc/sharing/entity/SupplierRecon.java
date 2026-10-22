package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商对账单（supplier_recon）。
 * 参照行业方案应付管理 + 供应商对账 + 行业方案应付管理最佳实践。
 * 状态流转：DRAFT 已生成 → SENT 对账函已发送 → CONFIRMED 双方确认 / DIFF 有差异 → RESOLVED 差异已处理。
 * 供应商确认：PENDING 待确认 → CONFIRMED 供应商确认一致 / REJECTED 供应商有异议。
 */
@Data
@TableName("supplier_recon")
public class SupplierRecon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long supplierId;
    private String supplierName;
    private String reconNo;
    /** 对账期间 yyyyMM */
    private String period;

    /** 应付合计（期初余额 + 本期应付发生额，累计应付） */
    private BigDecimal payableAmount;
    /** 已付合计（累计已付） */
    private BigDecimal paidAmount;
    /** 余额（应付合计 - 已付合计，向后兼容） */
    private BigDecimal balance;

    /** 期初余额（截至上期的累计应付 - 累计已付） */
    private BigDecimal openingBalance;
    /** 本期应付发生额（本期发票价税合计） */
    private BigDecimal periodPayable;
    /** 本期已付发生额（本期付款金额） */
    private BigDecimal periodPaid;
    /** 期末余额（期初 + 本期应付 - 本期已付） */
    private BigDecimal closingBalance;

    /** 对账类型：AUTO 自动对账 / MANUAL 手工对账 */
    private String reconType;

    /** 对账函编号 */
    private String letterNo;
    /** 对账函状态：NONE 未发送 / SENT 已发送 / RECEIVED 已回函 */
    private String letterStatus;
    /** 对账函发出时间 */
    private LocalDateTime letterSentTime;
    /** 供应商回函时间 */
    private LocalDateTime letterReceivedTime;

    /** 供应商确认状态：PENDING 待确认 / CONFIRMED 确认一致 / REJECTED 有异议 */
    private String supplierConfirmStatus;
    /** 供应商确认时间 */
    private LocalDateTime supplierConfirmTime;
    /** 供应商确认备注 */
    private String supplierConfirmRemark;

    /** LinkU同步状态：NONE 未同步 / SYNCED 已同步 / FAILED 同步失败 */
    private String linkuSyncStatus;
    /** LinkU同步时间 */
    private LocalDateTime linkuSyncTime;
    /** LinkU侧对账单ID */
    private Long linkuReconId;
    /** LinkU侧供应商ID(partnerId) */
    private Long linkuPartnerId;

    /** DRAFT已生成/SENT已发函/CONFIRMED双方确认/DIFF有差异/RESOLVED差异已处理 */
    private String status;
    private String diffRemark;
    private LocalDateTime confirmTime;
    private Long confirmBy;
    private String remark;
    /** 所属公司（多分子公司审批路由） */
    private Long companyId;
    // 与 X-34 资金四表同型裸字段：不标 fill 则 MP 的 INSERT/UPDATE 列清单不含该列，MetaObjectHandler 填值无效
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Integer delFlag;
}
