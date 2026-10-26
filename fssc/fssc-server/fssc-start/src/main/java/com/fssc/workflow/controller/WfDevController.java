// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 开发/测试工具（/api/tenant/wf/dev/**）：
 * 通过真实流程引擎（WfEngineFacade.start）发起任意已部署流程，供全流程自动化测试使用。
 *
 * <p>用途：校验「节点链路 / 角色路由 / 金额分档 / 并行会签」与设计稿（wf_business_flow.node_json）一致。
 * 本工具不创建业务单据（bizId 用自增假 ID，仅用于流程变量占位），也不绕过引擎——
 * 仍走真实 Flowable 定义、动态办理人解析、越权守卫与流程审计双写。</p>
 *
 * <p>安全说明（P0 修复）：本工具可<b>以任意身份启动流程</b>、<b>取消任意流程实例</b>，
 * 属流程引擎级高危操作，2026-09-16 权限审计前<b>无任何权限注解</b>，任何登录用户均可调用
 * （实测 E2E 脚本即以普通总账会计账号 Y003739 调用成功）。
 * 现收敛为 {@code wf:model:update}（流程建模/部署权，仅授予配置管理类角色），
 * 并保留 {@code fssc.wf.dev-tool-enabled} 开关供生产直接下线。</p>
 */
@RestController
@RequestMapping("/api/tenant/wf/dev")
@RequiredArgsConstructor
public class WfDevController {

    private final WfEngineFacade wfEngineFacade;

    /** P1-4：流程调试工具开关，默认开启（联调/自动化测试用），生产置 false */
    @Value("${fssc.wf.dev-tool-enabled:true}")
    private boolean devToolEnabled;

    /**
     * ST-03：取消僵尸流程实例（测试数据卫生；PROCESS_CANCELLED 不触发业务收尾事件）
     *
     * <p>D-DEC-455：本方法曾与类注释宣称的办结不一致——{@code /start} 与
     * {@code /instance/refresh-biz-no} 都挂了权限校验，唯独这条「取消任意实例」的环节裸奔，
     * 任何登录用户都能作废他人 in-flight 流程。现与同族端点同码办结，并补 {@code @OperLog}
     * 让作废动作进 {@code sys_oper_log}（此前只在 {@code wf_task_history} 留 CANCEL，
     * 平台侧记录零痕迹）。业务单据的正当作废出口＝D-DEC-454 的 {@code pay-plan/{id}/void}，
     * 该端点会同步回写单据状态，不需要走这里。</p>
     */
    @RequiresPerm({"wf:design:update", "wf:model:update"})
    @OperLog(module = "WF_DEV", operType = "OTHER", desc = "取消流程实例（开发/运维清理，不触发业务收尾）")
    @PostMapping("/instance/cancel")
    public Result<Void> cancelInstance(@RequestBody Map<String, Object> body) {
        if (!devToolEnabled) {
            return Result.fail(403, "流程调试工具已禁用（fssc.wf.dev-tool-enabled=false）");
        }
        wfEngineFacade.cancelInstance(String.valueOf(body.get("procInstId")),
                String.valueOf(body.getOrDefault("reason", "ST-03 僵尸实例清理")));
        return Result.success();
    }

    /** 自增假业务ID：仅用于流程变量占位，避免与真实单据冲突（时间戳高位+序号，落 Long 安全区间） */
    private static final AtomicLong ID_SEQ = new AtomicLong(System.currentTimeMillis() % 100000000L + 8000000000L);

    /**
     * 批次3 R3-C（D-B3-03）：按实例补救业务单据号（去重，单实例）。
     *
     * <p>请求体：{@code instanceId}（{@code wf_instance.id}）。行为与发起时同源：重新解析单据号，
     * 只在"解析得到且与现值不同"时写回 {@code wf_instance.biz_no}（并同步 {@code wf_task.biz_no} 冗余列）；
     * 解析不到 ⇒ 保持原值，<b>不会</b>把已有单号刷成 null。重复调用同一实例结果一致。</p>
     *
     * <p>不提供批量入口，也不自动回填 2026-09-23 之前的 32 条历史 NULL——那批属裁决项（V1.4.37 备而未执行）。
     * 为什么放在 dev 工具下：它写的是流程侧展示列，不改业务表、不改流转状态，但可被用来伪造单号列，
     * 故与 {@code /start} 同级办结在 {@code wf:model:update} + {@code dev-tool-enabled} 之后。</p>
     *
     * @return instanceId / updated（0 或 1；0 的原因见服务端日志）
     */
    @RequiresPerm({"wf:design:update", "wf:model:update"})
    @PostMapping("/instance/refresh-biz-no")
    public Result<Map<String, Object>> refreshBizNo(@RequestBody Map<String, Object> body) {
        if (!devToolEnabled) {
            return Result.fail(403, "流程调试工具已禁用（fssc.wf.dev-tool-enabled=false）");
        }
        Object id = body.get("instanceId");
        if (id == null || String.valueOf(id).isBlank()) {
            return Result.fail(400, "instanceId 必填（wf_instance.id）");
        }
        int updated = wfEngineFacade.refreshBizNo(Long.valueOf(String.valueOf(id).trim()));
        Map<String, Object> data = new HashMap<>();
        data.put("instanceId", id);
        data.put("updated", updated);
        return Result.success(data);
    }

    /**
     * 发起任意流程（走真实引擎）。
     *
     * <p>请求体：</p>
     * <ul>
     *   <li>{@code bizType} 必填：wf_business_flow.biz_type（即流程 model_key）；</li>
     *   <li>{@code title} 可选：流程标题（默认 DEV-{bizType}），建议带唯一标记便于检索；</li>
     *   <li>{@code amount} 可选：金额（金额分档流程建议传大额 6000000 触发最高档，覆盖全节点链路）；</li>
     *   <li>{@code subType} 可选：子类型（如 CLAIM 的 TRAVEL 分支）。</li>
     * </ul>
     *
     * @return procInstId / bizId / bizType / amount / title
     */
    @RequiresPerm({"wf:design:update", "wf:model:update"})
    @PostMapping("/start")
    public Result<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        // P1-4：生产环境可通过 fssc.wf.dev-tool-enabled=false 关闭该调试入口
        if (!devToolEnabled) {
            return Result.fail(403, "流程调试工具已禁用（fssc.wf.dev-tool-enabled=false）");
        }
        String bizType = body.get("bizType") == null ? null : String.valueOf(body.get("bizType"));
        String subType = body.get("subType") == null ? null : String.valueOf(body.get("subType"));
        String title = body.get("title") == null || String.valueOf(body.get("title")).isBlank()
                ? "DEV-" + bizType : String.valueOf(body.get("title"));
        BigDecimal amount = body.get("amount") == null
                ? null : new BigDecimal(String.valueOf(body.get("amount")));
        Long bizId;
        if (body.get("bizId") != null && !String.valueOf(body.get("bizId")).isBlank()) {
            // 允许传入真实业务ID（用于针对具体单据驱动流程、验证收尾动作），缺省仍用自增假 ID
            bizId = Long.valueOf(String.valueOf(body.get("bizId")).trim());
        } else {
            bizId = ID_SEQ.incrementAndGet();
        }

        String procId = wfEngineFacade.start(bizType, bizId, null, title, amount, subType);

        Map<String, Object> data = new HashMap<>();
        data.put("procInstId", procId);
        data.put("bizId", bizId);
        data.put("bizType", bizType);
        data.put("amount", amount == null ? null : amount.toPlainString());
        data.put("title", title);
        return Result.success(data);
    }
}
