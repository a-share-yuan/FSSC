package com.fssc.workflow.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * G3（2026-09-24）共享作业分配策略与双时限配置（op_dispatch_policy，带 tenant_id）。
 *
 * 一条记录 = 某个流程节点的分配策略 + 两档时限；{@code model_key='*' AND node_key='*'} 是全局默认行。
 *
 * <p><b>为什么策略配置要落表而不是写死代码</b>：本仓库既有教训是"配置界面向业务承诺引擎从来没做的事"
 * （方案 §二 F6~F8 伪配置）。本表反过来——<b>列在表里、代码里有读取点</b>，
 * 读取点见 {@code DispatchPolicyService}，配置改了行为就变。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_dispatch_policy")
public class OpDispatchPolicy extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 流程定义 key；{@code *} 表示全局默认行 */
    private String modelKey;
    /** 节点 ID（BPMN userTask id）；{@code *} 表示全局默认行 */
    private String nodeKey;
    /** 策略名称 */
    private String policyName;
    /** 分配策略：PICK抢单 / BALANCE_TIME均衡按时 / BALANCE_VOLUME均衡按量 / SMART智能 */
    private String strategy;
    /** 普通任务时限（小时），通行口径默认 24h */
    private BigDecimal normalHours;
    /** 加急任务时限（小时），通行口径默认 4h */
    private BigDecimal urgentHours;
    /** 均衡·按时的分配间隔（小时），默认 1h 一分 */
    private BigDecimal balanceIntervalHours;
    /** 均衡·按量的累积批大小（单），默认 10 单一分配 */
    private Integer balanceBatchSize;
    /** 智能策略权重：在手量 */
    private BigDecimal smartWeightVolume;
    /** 智能策略权重：时限紧迫度 */
    private BigDecimal smartWeightUrgency;
    /** 智能策略权重：任务优先级 */
    private BigDecimal smartWeightPriority;
    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;
    /** 备注 */
    private String remark;

    /** 策略枚举归一（未知值一律按抢单 PICK 处理：宁可让人工认领，也不做"看起来自动其实乱派"） */
    @JsonIgnore
    public String normalizedStrategy() {
        if (strategy == null || strategy.isBlank()) {
            return "PICK";
        }
        String s = strategy.trim().toUpperCase();
        return switch (s) {
            case "BALANCE_TIME", "BALANCE_VOLUME", "SMART", "PICK" -> s;
            default -> "PICK";
        };
    }
}
