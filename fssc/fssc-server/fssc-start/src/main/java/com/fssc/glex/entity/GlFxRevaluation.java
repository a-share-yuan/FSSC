// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

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
 * 月末外币汇兑重估流水（gl_fx_revaluation，带 tenant_id，无逻辑删除列）。
 * 一条记录 = 一个 租户+期间+公司+币种+方向(AP/AR/LOAN) 组合的重估结果，uk 防重复。
 *
 * <p>独立实体（不继承 TenantAuditBase）：本表无 del_flag/update_by 等审计列，
 * 避免基类 {@code @TableLogic} 自动追加 del_flag 过滤导致列不存在。
 */
@Data
@TableName("gl_fx_revaluation")
public class GlFxRevaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 会计期间 yyyyMM */
    private String period;
    /** 公司ID（应收取租户首个公司兜底） */
    private Long companyId;
    /** 外币币种 */
    private String fromCurrency;
    /** 应收应付方向：AP外币应付 / AR外币应收 / LOAN员工外币借款 */
    private String itemType;
    /** 纳入重估的单据/明细条数 */
    private Integer bookCount;
    /** 账面本位币（按入账汇率折算的期末余额） */
    private BigDecimal bookLocal;
    /** 重估本位币（原币余额×期末汇率） */
    private BigDecimal revalLocal;
    /** 汇兑差异 = 重估本位币 − 账面本位币 */
    private BigDecimal diff;
    /** 期末汇率（外币→CNY；缺失=SKIPPED 时为空） */
    private BigDecimal rate;
    /** 生成的重估凭证 op_voucher.id（DRAFT；SKIPPED 为空） */
    private Long voucherId;
    /** GENERATED 已生成凭证 / SKIPPED 未生成 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
