package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参与人方案下的取人维度（wf_assignee_dim，带 tenant_id）—— 一条记录＝一个维度。
 *
 * <p><b>为什么维度要独立成行而不是塞进方案的 JSON</b>：维度是配置面要逐条增删改的实体（配置页一行一个维度），
 * 也是"这个节点为什么解析到他"的可追溯单位（预览接口按维度逐条回显命中人数）；JSON 列会让两者都变难。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_assignee_dim")
public class WfAssigneeDim extends TenantAuditBase {

    /** 所属方案 ID（wf_assignee_scheme.id） */
    private Long schemeId;
    /**
     * 维度类型：
     * {@code USER} 指定人 / {@code ROLE} 角色 / {@code POST} 岗位 / {@code DEPT} 部门 /
     * {@code DEPT_TREE} 部门含下级 / {@code DEPT_LEADER} 部门负责人 /
     * {@code REPORT_TO} 汇报关系 / {@code VARIABLE} 参与人变量。
     */
    private String dimType;
    /**
     * 维度取值，多个逗号分隔；含义随 dimType 而定：
     * USER＝用户ID；ROLE＝角色编码；POST＝岗位ID或岗位编码；DEPT/DEPT_TREE/DEPT_LEADER＝部门ID；
     * REPORT_TO＝主体用户ID（留空＝发起人）；VARIABLE＝流程变量名。
     */
    private String dimValue;
    /**
     * 仅 {@code REPORT_TO} 使用：{@code DIRECT_UP} 直接上级 / {@code INDIRECT_UP} 间接上级 /
     * {@code PEER} 平级 / {@code DIRECT_DOWN} 直接下级 / {@code INDIRECT_DOWN} 间接下级。
     */
    private String relation;
    /** 仅 {@code REPORT_TO} 使用：级数（1＝直接，2＝隔一级…；与 wf_node_config.up_level 同口径，最多 10） */
    private Integer upLevel;
    /** 仅 {@code DEPT} 使用：是否含下级部门（0否 1是）；{@code DEPT_TREE} 恒视为 1 */
    private Integer includeChildren;
    /** 方案内排序（越小越先，影响 AND/EXCLUDE 的基集选择与预览回显顺序） */
    private Integer sort;
}
