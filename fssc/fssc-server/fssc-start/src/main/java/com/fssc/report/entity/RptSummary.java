package com.fssc.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报表预聚合（rpt_summary） */
@Data
@TableName("rpt_summary")
public class RptSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 期间 yyyyMM */
    private String period;
    /** 维度 EXPENSE_TYPE/COMPANY/DEPT */
    private String dimType;
    private String dimKey;
    private String dimName;
    private Long billCount;
    private BigDecimal totalAmount;
    private LocalDateTime refreshTime;
}
