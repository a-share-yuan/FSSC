package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 智能审单日志（op_review_log，带 tenant_id）。记录每次审单执行结果。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_review_log")
public class ReviewLog extends TenantBase {

    /** 单据ID */
    private Long billId;

    /** 单据编号 */
    private String billNo;

    /** 规则ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 匹配结果：true-匹配 false-未匹配 */
    private Boolean matched;

    /** 执行动作：AUTO_PASS/AUTO_REJECT/MANUAL_REVIEW/SKIP */
    private String action;

    /** 风险等级 */
    private String riskLevel;

    /** 风险评分 */
    private Integer riskScore;

    /** 说明 */
    private String remark;

    /** 审核人（MANUAL_REVIEW 时） */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 最终状态：PASS/REJECT/PENDING */
    private String finalStatus;

    /** 记录人（D-DEC-175：TenantBase 无审计列，MP 自动填充够不到本列，须写点显式置值；表列为 varchar） */
    private String createBy;
}