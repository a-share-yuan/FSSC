// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.gl.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 总账分录（op_gl_entry，带 tenant_id）。
 * 凭证 POSTED 时逐行写入（唯一键 voucher_id+line_no 防重复），
 * 反向凭证不产生冲销行，直接写入方向相反的新行，余额通过聚合实时计算。
 *
 * <p><b>（证据 12-30:201）</b>：表内 18 列<b>没有任何操作人列</b>，过账人只存在
 * {@code op_voucher.post_user_id} 一处 ⇒ 从分录这一侧看到"借 2211 应付职工薪酬 65,000"，
 * 却答不出"谁、在哪个动作下把它入账"（{@code create_time} 由 {@code postVoucherToGl} 用
 * {@code LocalDateTime.now()} 直填，且无 {@code create_by}），审计必须再跳一层凭证表。
 * 写入位置代码已就位（{@code GlService.postVoucherToGl(voucherId, operatorId)}），
 * <b>下面两个字段目前是 {@code exist=false}</b>：列未写入数据库前不参与 SQL，行为与原先逐字一致。
 *
 * <pre>
 * -- 需产品确认：以下 DDL 未执行、也不得由缺陷修复批次执行。
 * -- 执行后再把本类两行 @TableField(exist = false) 改为 @TableField("post_user_id") / @TableField("post_user_name")
 * -- （或直接删掉注解，MP 的下划线转驼峰会自动对上）。
 * -- ALTER TABLE op_gl_entry
 * ADD COLUMN post_user_id BIGINT NULL COMMENT '过账人ID（取自 op_voucher.post_user_id 或落账触发人）' AFTER biz_type,
 * ADD COLUMN post_user_name VARCHAR(64) NULL COMMENT '过账人姓名（冗余，防离职后查不到）' AFTER post_user_id;
 * --
 * -- 回填历史（同一判定里一起做，否则老分录仍是空值）：
 * -- UPDATE op_gl_entry e JOIN op_voucher v ON v.id = e.voucher_id
 * SET e.post_user_id = v.post_user_id WHERE e.post_user_id IS NULL;
 * -- 取不到的（历史凭证 post_user_id 亦空）再退 v.create_by 对应的 sys_user.id，仍取不到就保持为空——
 * -- 宁可空，也不给分录编一个"当时恰好点按钮的人"（同 规则）。
 * --
 * -- 同族未一并处理的（本批次范围外，登记备查）：op_bank_statement 无操作人列、。
 * </pre>
 */
@Data
@TableName("op_gl_entry")
public class GlEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    private Long voucherId;
    private String voucherNo;
    private Long voucherLineId;
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    /** DEBIT借方 / CREDIT贷方 */
    private String direction;
    private BigDecimal amount;
    private String summary;
    /** 业务单据ID（billId） */
    private Long bizId;
    /** 业务类型（sourceType，如 EXP_CLAIM / EXPENSE_PAY） */
    private String bizType;
    /**
     * 过账人 ID —— 库里暂无此列，{@code exist=false} 保证不拼进 SQL（零运行时影响）。
     * 列建好后改注解即可生效，见类注释里的注释态 DDL（需产品确认）。
     */
    @TableField(exist = false)
    private Long postUserId;
    /** 过账人姓名（同上，冗余一列免离职失联） */
    @TableField(exist = false)
    private String postUserName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}