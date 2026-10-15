package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点审批人配置（wf_node_config，带 tenant_id）。
 * 页面可配审批人，禁止写死在 BPMN 里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_node_config")
public class WfNodeConfig extends TenantAuditBase {

    /** 流程定义 key */
    private String modelKey;
    /** 节点 ID（BPMN userTask id） */
    private String nodeKey;
    private String nodeName;
    /** USER指定人/ROLE角色/POST岗位/DEPT_LEADER部门负责人/DEPT_LEADER_UP逐级上级/INITIATOR发起人/SHARE_POOL共享池/EXPRESSION表达式 */
    private String assignType;
    /** 审批人取值，多个逗号分隔 */
    private String assignValue;
    /**
     * 取人方式（与 assignType 正交：assignType 定"从哪个范围取"，本字段定"怎么定到人"）：
     * AUTO 按配置自动指派 / PICK 由上一环节办理人从候选集指定一人 / POOL 进任务池领取。
     * <p>缺省 AUTO，与 handling_mode 列存在前的行为完全一致。POOL 尚无实现（依赖
     * {@code op_audit_task} 与流程实例打通），配置页暂不开放该档。
     */
    private String handlingMode;
    /** 逐级上级时向上层数 */
    private Integer upLevel;
    /**
     * 多签类型：ANY任一通过 / ALL会签 / SEQ顺序审批 / VOTE票签。
     * <p><b>P1 已落地（2026-09-23）</b>：WfBpmnTemplateService 在生成 BPMN 时按 node_key 读取本字段，
     * 对 ALL/SEQ/VOTE 节点生成 {@code multiInstanceLoopCharacteristics}（集合=运行期注入的候选用户列表）；
     * VOTE 再以 {@code voteRatio} 生成比例通过完成条件 + WfEngineFacade 计票（wf_task_vote）。
     * 仅 ANY 与单办理人模型一致（候选组任一人认领并完成即推进）。未配置或为空 → 不生成多实例（零行为变更）。
     */
    private String multiType;
    /** 票签通过比例(%) */
    private Integer voteRatio;
    /** 允许转办：0 时人工转办被 WfEngineFacade.transfer 直接拒绝；超时自动转办不受此开关约束 */
    private Integer allowTransfer;
    /**
     * 允许加签：0 时 {@code WfEngineFacade.addSign} 拒绝（F3-3 接真，体检 E-06a）。
     *
     * <p>此前该列<b>三重落空</b>：实体不映射、节点配置界面不显示、运行时零读取——
     * 现网 355 个有效节点里 311 个配 0、44 个配 1（2026-09-26 只读普查），配了却从不生效。
     * 拦截总闸 {@code approve.addsign_gate.enabled} 缺省 0＝不拦，保持现行为；
     * 待办 VO 里的 {@code addSignAllowed} 也只在总闸打开时才下发（前端据此隐藏按钮）。
     */
    private Integer allowAddsign;
    /** ADMIN转管理员/SKIP自动跳过/ERROR报错阻断 */
    private String emptyStrategy;
    /** 审批人=发起人时自动通过 */
    private Integer autoPassSelf;
    /** 超时小时数，空表示不限 */
    private Integer timeoutHours;
    /** NOTIFY催办/AUTO_PASS自动通过/AUTO_TRANSFER自动转办 */
    private String timeoutAction;
    /** 本节点字段权限 JSON */
    private String formPerm;
    /**
     * G2 传阅（2026-09-23）：本节点办结后抄送的用户ID，逗号分隔。
     *
     * <p>与运行时入参 {@code ccUserIds}（审批人临时指定）在 complete() 里取<b>并集</b>后投递。
     * 与 timeout_hours/up_level 同一条口径：按 model_key + node_key <b>精确匹配</b>，
     * node_key 不在已部署 BPMN userTask 集合内的「孤儿行」配置一律不读。
     */
    private String ccUsers;
    /** G2 传阅：本节点办结后抄送的角色编码，逗号分隔（运行期展开为在职成员） */
    private String ccRoles;
    private Integer sort;

    // ==================== G4 治理新增列（2026-09-24）====================

    /**
     * F10「节点名不承载控制流」：首节点是否"提单即完成"（提单动作本身，不产生人工待办）。
     *
     * <p><b>为什么要有它</b>：改造前该语义由 {@code WfEngineFacade.autoSkipSelfConfirmFirstNode}
     * 按 {@code firstTask.getName().contains("提交")} 的子串判定——<b>改一个节点名就改变了控制流</b>，
     * 是不可测试、不可审计的隐式契约。现落到本列显式承载。
     *
     * <p><b>取值</b>：{@code 1}=自动通过；{@code 0}=显式关闭；
     * {@code NULL}=未配置——过渡期回退按旧判据（节点名含"提交"）判定，保证上线行为零变化。
     * 待旧判据可观测后即可摘除回退分支，届时本列成为唯一真值。
     */
    private Integer firstNodeAutoPass;

    // ==================== G2 原生能力替换新增列（2026-09-24）====================

    /**
     * G2 驳回范围闸门：{@code NONE}（默认，不允许指定节点驳回）/ {@code PREV_ONE}（只允许退回紧邻上一节点）/
     * {@code ANY_DONE}（允许退回任一已完成节点）。
     *
     * <p><b>为什么要有它</b>：指定级次驳回走的是 Flowable 的 {@code moveActivityIdTo}（属"搬执行"，
     * 会真实改动在办单据本体），此前唯一的闸门是<b>私有</b>全局开关 {@code isFreeRejectEnabled()}
     * ——要么全开要么全关，无法按节点分级。现把"谁能退、能退多远"落到节点配置上，全局开关退化为
     * 总闸（kill-switch）继续保留：生效条件＝本列 ≠ {@code NONE} <b>且</b> 总闸 = 1。
     */
    private String rejectScope;

    /**
     * 1 = 本节点驳回后，把本节点 key 记入实例变量 {@code rejectOriginNode}；单据重新提交时跳回本节点重办。
     * 0 = 关闭（默认，行为与改造前一致）。
     */
    private Integer resubmitJumpBack;

    /**
     * {@code RATIO}（默认，按百分比）/ {@code COUNT}（按确定票数）。
     *
     * <p>与遗留列 {@code vote_ratio} 的关系：本列 + {@code voteValue} 存在时<b>优先</b>，
     * {@code vote_ratio} 仅作为二者缺省时的回退（保证存量行行为不变）。
     */
    private String voteMode;

    /** G2 票签阈值取值：{@code vote_mode=RATIO} 时为百分比 1~100；{@code COUNT} 时为所需票数。 */
    private Integer voteValue;

    /**
     * {@code BY_VOTE}（分母＝已投票数，即"实到"）。
     */
    private String voteBasis;

    /**
     * G2 不达票兜底：多实例会签全部办完但票数未达阈值时，落哪个默认审批项并让流程继续。
     * {@code REJECT}（默认）/ {@code PASS}。
     *
     * <p><b>为什么要显式定义</b>：此前"不达票"没有任何语义——多实例既可能因完成条件永不满足而卡死，
     * 也可能被引擎默认收敛，两种结果都无法解释、无法统计。现固定为"落默认审批项 + 流程继续"，
     * 并写一条 {@code VOTE_NOT_REACHED} 留痕供质量报表统计。
     */
    private String voteFallback;
}
