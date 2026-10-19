package com.fssc.workflow.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfProcessLog;
import com.fssc.workflow.service.WfProcessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程处理详细日志（{@code /api/tenant/wf/process-log/**}）——管理中心「流程日志」视图，
 *
 * <p><b>权限码选择</b>：日志属流程管理中心的诊断视图，与 {@code /wf/perf}（效率聚合）同档，
 * 复用 {@code wf:instance:list}（已授予管理侧角色），<b>不新造</b> {@code wf:log:*}——
 * 新造码意味着上线后所有角色都拿不到、页面直接 403（G3 已确立的取舍，此处沿用）。
 * 开关的写操作复用 {@code wf:config:update}（参数属配置面）。
 */
@RestController
@RequestMapping("/api/tenant/wf/process-log")
@RequiredArgsConstructor
public class WfProcessLogController {

    private final WfProcessLogService processLogService;

    /** 日志分页查询（可按实例/单号/单据类型/日志类型/时间范围过滤） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/page")
    public Result<PageResult<WfProcessLog>> page(PageQuery q,
                                                 @RequestParam(required = false) String procInstId,
                                                 @RequestParam(required = false) String bizNo,
                                                 @RequestParam(required = false) String bizType,
                                                 @RequestParam(required = false) String logType,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return Result.success(processLogService.page(q, procInstId, bizNo, bizType, logType,
                parseDayStart(from), parseDayEnd(to)));
    }

    /** 某实例的完整日志时间线（升序）——排查"这单为什么卡住"的主入口 */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/by-instance")
    public Result<List<WfProcessLog>> byInstance(@RequestParam String procInstId) {
        return Result.success(processLogService.byInstance(procInstId));
    }

    /**
     * 读取详细日志开关状态。
     *
     * <p>同时返回 {@code cfgKey}：让运营知道"这个开关在参数表里的哪一行"，
     * 避免出现"页面有开关、参数表另有一份"的双真值。
     */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/switch")
    public Result<Map<String, Object>> switchState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cfgKey", WfProcessLogService.CFG_KEY_DETAILED);
        m.put("enabled", processLogService.enabled());
        m.put("hint", "关闭（缺省）时流程处理详细日志一行不落库；勾选后立即生效（无需重启）。"
                + "开关值存于参数表 sys_config，本接口只读。");
        return Result.success(m);
    }

    /**
     * 按保留天数清理历史日志（物理删除）。
     *
     * <p>为什么必须提供：详细日志按事件逐条累积，没有清理通道就会无限膨胀，
     * 最终没人敢长期打开这个开关。日志不是审计凭证（审计在 {@code wf_task_history}），可安全清理。
     */
    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_PROCESS_LOG", operType = "DELETE", desc = "清理流程处理详细日志")
    @PostMapping("/clean")
    public Result<Integer> clean(@RequestParam(defaultValue = "30") int keepDays) {
        return Result.success(processLogService.cleanByRetentionDays(keepDays));
    }

    private static LocalDateTime parseDayStart(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim()).atStartOfDay();
        } catch (Exception ex) {
            return null;
        }
    }

    private static LocalDateTime parseDayEnd(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim()).atTime(23, 59, 59);
        } catch (Exception ex) {
            return null;
        }
    }
}