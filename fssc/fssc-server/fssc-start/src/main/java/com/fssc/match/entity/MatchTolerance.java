package com.fssc.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 三单匹配容差参数表（match_tolerance，公司级）。
 * 收货金额 vs 发票金额的匹配容差参数化：百分比（amount_pct）+ 绝对值（amount_abs）双轨，
 * 供 MatchToleranceService / ThreeWayMatchService 做容差判定（区别于 md_match_tolerance 的维度键值型配置）。
 * 判定规则：差异 ≤ max(收货金额 × amount_pct / 100, amount_abs) 视为容差内。
 * 无公司配置或 status=DISABLED 时按默认参数 0.5% / 100 元处理。
 */
@Data
@TableName("match_tolerance")
public class MatchTolerance implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 所属公司（sys_company.id），按公司维度配置容差 */
    private Long companyId;
    /** 金额容差百分比（%，DECIMAL(10,4)）：0.5 = 0.5% */
    private BigDecimal amountPct;
    /** 金额容差绝对值（元，DECIMAL(18,2)）：100 = 100 元 */
    private BigDecimal amountAbs;
    /** NORMAL启用 / DISABLED停用（停用或无配置按默认 0.5% / 100 元处理） */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
