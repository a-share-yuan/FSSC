// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 节点参与人方案（wf_assignee_scheme，带 tenant_id）—— G2「参与人解析：维度组合 + 参与人方案」的方案层。
 *
 * <p><b>为什么要有它</b>：{@code wf_node_config.assign_type} 只实现了 USER/ROLE/INITIATOR/DEPT_LEADER/
 * DEPT_LEADER_UP 五档，POST/EXPRESSION/SHARE_POOL 等配置落进 {@code default} 分支后被<b>静默 no-op</b>
 * ——配置者以为"这个节点配了岗位取人"，运行时其实没人被指派，只能靠往主数据里铺角色/补岗绕行。
 * 本表把"取人"从"单一 assign_type 枚举"升级为「多套方案（优先级 + 条件）＋ 方案内多维度组合」：
 * 一个节点可以配多套方案，按 {@code priority} 升序逐套试，高优先级方案解析不出人时降级到下一套，
 * 全部方案都解析不出＝解析失败并冒泡（不允许静默留一个没人可办的候选组）。
 *
 * <p><b>与 wf_node_config.assign_type 的关系</b>：两者并存、互不覆盖——{@code assign_type} 的五档仍是
 * 既有行为（零变化），本表只在 assign_type 落进 default（含 POST/SHARE_POOL/EXPRESSION）时参与取人；
 * <b>完全没配本表＝保持原 no-op 行为逐字不变</b>（这是默认零变化的硬约束）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_assignee_scheme")
public class WfAssigneeScheme extends TenantAuditBase {

    /** 流程定义 key（与 wf_node_config.model_key 同源） */
    private String modelKey;
    /** 节点 ID（BPMN userTask id，与 wf_node_config.node_key 同源，精确匹配） */
    private String nodeKey;
    /** 优先级：<b>数值小者先试</b>（与 wf_process_config.priority"数值大者优先"相反，此处按"第 1 套、第 2 套"的自然读法） */
    private Integer priority;
    /**
     * 条件表达式（可空＝无条件适用）。空即"本方案总是参与解析"。
     *
     * <p>支持的最小文法（刻意不做通用脚本求值，避免配置面变成代码注入面）：
     * <ul>
     *   <li>变量：{@code amount} / {@code bizType} / {@code subType} / {@code companyId} / {@code deptId} /
     *       {@code startUserId}，以及流程实例变量原名；</li>
     *   <li>比较：{@code == != > >= < <=}，右值可写数字或单引号字符串，如 {@code amount >= 10000}、
     *       {@code bizType == 'EXP_CLAIM'}；</li>
     *   <li>组合：{@code &&}（与）、{@code ||}（或），如 {@code bizType == 'EXP_CLAIM' && amount > 5000}。</li>
     * </ul>
     * 取不到的变量 ⇒ 该比较为假；表达式整体无法解析 ⇒ 该方案视为"条件不满足"降级到下一优先级（并留 warn）。
     */
    private String conditionExpr;
    /**
     * 方案内维度组合方式：{@code OR}（默认，各维度取并集）/ {@code AND}（各维度取交集）/
     * {@code EXCLUDE}（本方案各维度构成"排除集"，从本节点 BPMN 候选组解析出的候选人中扣除）。
     */
    private String combineMode;
    /** 是否启用：1启用 0停用（停用行不参与解析，便于灰度/回滚） */
    private Integer enabled;
    /** 同优先级内的排序（越小越先） */
    private Integer sort;
}
