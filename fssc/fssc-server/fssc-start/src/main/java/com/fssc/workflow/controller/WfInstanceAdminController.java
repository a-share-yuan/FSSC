package com.fssc.workflow.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.workflow.service.WfQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * <b>业务级流程撤销入口</b>。
 *
 * <h3>要解决的实际问题</h3>
 * 清理僵尸/悬挂流程实例原先<b>只能走 {@code POST /api/tenant/wf/dev/instance/cancel}</b>，
 * 而它是<b>开发调试工具</b>：
 * <ul>
 *   <li>权限是 {@code wf:design:update} / {@code wf:model:update}（<b>流程设计权限</b>），
 *       业务管理员通常根本没有；</li>
 *   <li>而「流程卡住、待办点进去是空的」恰恰是<b>业务侧最常遇到</b>的问题。</li>
 * </ul>
 * ⇒ <b>权限与场景错配</b>：真正需要它的人用它不了，有它的人不该用它改业务单。
 *
 * <h3>本类提供两级入口（权限与场景对齐）</h3>
 * <ol>
 *   <li>{@code POST /api/tenant/wf/instance/{bizType}/{bizId}/withdraw}
 *       ——<b>发起人撤回自己提交的单</b>。校验发起人身份，无需额外权限。
 *       语义＝「我还没提交完/填错了，先把流程撤回来改」；
 *   <li>{@code POST /api/tenant/wf/instance/{bizType}/{bizId}/admin-cancel}
 *       ——<b>管理员强制撤销</b>，需 {@code wf:instance:admin} 权限，<b>必须填原因</b>，
 *       原因进流程记录（{@code WfEngineFacade#cancelInstance} 的 reason 参数）。</li>
 * </ol>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>只撤<b>RUNNING</b> 的实例（已办结/已驳回的不动，否则等于抹掉审计痕迹）；</li>
 *   <li><b>发起人校验</b>：withdraw 只允许 {@code startUserId == 当前登录人}，
 *       否则 403——防止 A 撤回 B 的单；</li>
 *   <li>不触发业务收尾（单据状态不变），与 dev 工具规则一致；</li>
 *   <li>管理员入口<b>强制填原因</b>，空原因直接 400，保证撤销可追溯。</li>
 * </ul>
 *
 * <p><b>与 的分工</b>：本类是<b>人工按需</b>撤销；
 * {@code WfZombieReconcileJob} 是<b>每日自动</b>办结「单据已最终状态/未提交却仍 RUNNING」的脱节实例。
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/wf/instance")
@RequiredArgsConstructor
public class WfInstanceAdminController {

    private final WfEngineFacade wfEngineFacade;
    private final WfQueryService wfQueryService;

    /**
     * ① 发起人撤回自己提交的单（无需额外权限，靠「是不是我发起的」判定）。
     *
     * @param bizType 业务类型（对应 {@code wf_instance.biz_type}，如 EXP_PAY / CONTRACT_APPROVE）
     * @param bizId业务单据主键（对应 {@code wf_instance.biz_id}）
     */
    @OperLog(module = "WF", operType = "CANCEL", desc = "发起人撤回自己提交的流程")
    @PostMapping("/{bizType}/{bizId}/withdraw")
    public Result<Integer> withdraw(@PathVariable String bizType, @PathVariable Long bizId) {
        Long me = UserContext.getRequired().getUserId();
        List<WfInstance> running = runningInstances(bizType, bizId);
        if (running.isEmpty()) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "该单据当前没有在办流程，无需撤回");
        }
        // 逐实例校验发起人：只要有一个不是本人发起的，就整体拒绝（避免部分撤销造成状态割裂）
        for (WfInstance inst : running) {
            if (inst.getStartUserId() == null || !inst.getStartUserId().equals(me)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "只能撤回自己发起的流程（该实例发起人 id=" + inst.getStartUserId()
                                + "，当前登录 id=" + me + "）；如需撤销他人流程请走管理员撤销入口");
            }
        }
        int n = 0;
        for (WfInstance inst : running) {
            wfEngineFacade.cancelInstance(inst.getProcInstId(),
                    "发起人主动撤回（用户 id=" + me + "，单据 " + bizType + "#" + bizId + "）");
            n++;
        }
        return Result.success(n);
    }

    /**
     * ② 管理员强制撤销（需 {@code wf:instance:admin} 权限，<b>必须填原因</b>）。
     *
     * @param body 至少包含 {@code reason}（撤销事由，进流程记录）
     */
    @RequiresPerm("wf:instance:admin")
    @OperLog(module = "WF", operType = "CANCEL", desc = "管理员强制撤销流程实例")
    @PostMapping("/{bizType}/{bizId}/admin-cancel")
    public Result<Integer> adminCancel(@PathVariable String bizType, @PathVariable Long bizId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : String.valueOf(body.get("reason"));
        if (reason == null || reason.isBlank() || "null".equals(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "管理员撤销必须填写原因（reason）：将写入流程记录，便于事后追溯");
        }
        List<WfInstance> running = runningInstances(bizType, bizId);
        if (running.isEmpty()) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "该单据当前没有在办流程，无需撤销");
        }
        int n = 0;
        for (WfInstance inst : running) {
            wfEngineFacade.cancelInstance(inst.getProcInstId(),
                    "管理员强制撤销（操作人 id=" + UserContext.getRequired().getUserId() + "）：" + reason);
            n++;
        }
        return Result.success(n);
    }

    /** 取该单据名下仍在 RUNNING 的实例；非 RUNNING 一律跳过（不碰已办结实例，保留审计痕迹）。 */
    private List<WfInstance> runningInstances(String bizType, Long bizId) {
        List<WfInstance> all = wfQueryService.instancesByBiz(bizType, bizId);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        return all.stream()
                .filter(i -> "RUNNING".equalsIgnoreCase(i.getStatus() == null ? "" : i.getStatus().trim()))
                .toList();
    }
}