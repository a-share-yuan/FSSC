package com.fssc.collab.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCP 供应商协同发票全流程单（AP 蓝图 SCC_AP_01~10、状态机4.3、打通点1~7）：
 * 待匹配 →(自动/手工匹配 R-16~18)→ 待寄送 →(创建邮包)→ 待签收 →(内网签收)→ 待扫描 →
 * 扫描+票核(R-20六要素) → 票核成功 →(记账·打通点3，受 R-14 关账冻结拦截)→ 记账成功 →
 * 集中认证(打通点6) → 认证完成；付款回写(打通点5)落在已记账/已认证单上。
 * 失败分支：匹配失败（重新匹配）、票核失败（复核后回到待匹配）。
 */
@Data
@TableName("collab_scp_bill")
public class ScpBill implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** SCP业务单号 */
    private String billNo;
    /** 对账单号（三单匹配最小单位/匹配批次号，打通点1） */
    private String reconBillNo;
    /** 预制发票号 = 对账单号+三位流水（R-31，打通点2） */
    private String preInvoiceNo;
    /** 发票号码/代码（抽取回填/扫描匹配键） */
    private String invoiceNo;
    private String invoiceCode;
    private String poNo;
    private String supplierCode;
    private String supplierName;
    /** 购方公司代码 */
    private String buyerCompanyCode;
    /** 专票/普票（供应商确认对账单时选择，R-30） */
    private String invoiceType;
    private String ccy;
    /** 发票金额/税额/价税合计 */
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate invoiceDate;
    /** 对账单侧基准金额/税额/价税合计（自动匹配比对） */
    private BigDecimal reconAmount;
    private BigDecimal reconTaxAmount;
    private BigDecimal reconTotal;
    /** 票核六要素 R-20：购方税号/销方税号 + 上方金额/税额/价税合计/发票日期 */
    private String buyerTaxNo;
    private String sellerTaxNo;
    /** 扫描件影像引用 */
    private String imageRef;
    private String courierCompany;
    private String courierNo;
    /** 打通点5 付款回写 */
    private String payVoucherNo;
    private LocalDate payDate;
    private BigDecimal payAmount;
    /** 打通点6 认证 */
    private String certifyResult;
    private LocalDate certifyDate;
    /** WAIT_MATCH/WAIT_SEND/MATCH_FAILED/WAIT_SIGN/WAIT_SCAN/CHECKING/CHECK_OK/CHECK_FAILED/POSTED/CERTIFIED/FORCE_PENDING/VOIDED */
    /** NONE/PENDING/APPROVED/REJECTED（R-18 强匹审批） */
    private String forceStatus;
    private String forceReason;
    private String forceApplyUser;
    private String forceApprUser;
    /** 审批驳回回退状态 */
    private String forcePrevStatus;
    private String status;
    private String failReason;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_WAIT_MATCH = "WAIT_MATCH";
    public static final String ST_WAIT_SEND = "WAIT_SEND";
    public static final String ST_MATCH_FAILED = "MATCH_FAILED";
    public static final String ST_WAIT_SIGN = "WAIT_SIGN";
    public static final String ST_WAIT_SCAN = "WAIT_SCAN";
    public static final String ST_CHECKING = "CHECKING";
    public static final String ST_CHECK_OK = "CHECK_OK";
    public static final String ST_CHECK_FAILED = "CHECK_FAILED";
    public static final String ST_POSTED = "POSTED";
    public static final String ST_CERTIFIED = "CERTIFIED";
    public static final String ST_FORCE_PENDING = "FORCE_PENDING";
    public static final String ST_VOIDED = "VOIDED";
}
