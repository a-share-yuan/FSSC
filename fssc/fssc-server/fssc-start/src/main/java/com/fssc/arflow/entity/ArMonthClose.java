package com.fssc.arflow.entity;

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
 * AR月末结账（蓝图月结管理 9.6，流程23/29应收月末结账 + 流程28/31成本月结，状态机4.5）：
 * 发起 → 清接口行（仅应收月结）→ 扛数据 → 差异调整 → 过总账 → 数据核对 → 关闭账期。
 * 关闭账期后当月不允许再录入业务（isClosed 供各入口拦截）。
 */
@Data
@TableName("ar_month_close")
public class ArMonthClose implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String closeNo;
    private Long companyId;
    /** yyyy-MM */
    private String period;
    /** RECEIVABLE应收月结/COST成本月结 */
    private String closeType;
    /** INIT/INTERFACE_CLEARED/DATA_PULLED/ADJUSTED/GL_POSTED/RECONCILED/CLOSED */
    private String status;
    /** 扛数快照-单据金额合计 */
    private BigDecimal pulledAmount;
    /** 扛数快照-单据数 */
    private Integer pulledCount;
    private BigDecimal glAmount;
    /** 核对差异金额 */
    private BigDecimal diffAmount;
    /** 1=差异未消除 */
    private Integer diffFlag;
    private String adjustNote;
    private String voucherNo;
    private String operator;
    private LocalDateTime closedTime;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String T_RECEIVABLE = "RECEIVABLE";
    public static final String T_COST = "COST";

    public static final String ST_INIT = "INIT";
    public static final String ST_INTERFACE_CLEARED = "INTERFACE_CLEARED";
    public static final String ST_DATA_PULLED = "DATA_PULLED";
    public static final String ST_ADJUSTED = "ADJUSTED";
    public static final String ST_GL_POSTED = "GL_POSTED";
    public static final String ST_RECONCILED = "RECONCILED";
    public static final String ST_CLOSED = "CLOSED";
    /** R5-D-218：作废终态（留痕不删行，解冻账期锁与期间的出口态） */
    public static final String ST_VOID = "VOID";
}
