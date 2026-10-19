package com.fssc.workflow.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.ops.service.TaskPoolQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 *
 * <p>五个视图：我的待办 / 可认领 / 被他人锁定 / 我已认领 / 池内全部（语义与取舍见
 * {@link TaskPoolQueryService} 类注释）。<b>超时列读 {@code wf_task.due_time} 真值</b>，
 * 未落时限的历史行返回 {@code dueTimeMissing=true}，前端据此提示"待补时限"而不是显示"未超时"。
 *
 * <p>权限复用既有 {@code wf:task:list}（与 {@code /wf/query/my-todo} 同族）：任务池是业务角色的
 * 日常工作台，不该被新造的权限码挡在门外。{@code POOL_ALL}（池内全部）是全中心总览，
 * 数据面比个人待办宽，故单独要求 {@code wf:instance:list}。
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/wf/pool")
@RequiredArgsConstructor
@Validated
public class WfTaskPoolController {

    private final TaskPoolQueryService poolQueryService;

    /** 视图清单（前端下拉数据源，避免前端硬编码视图名；R5-D-258①：按当前账号权限裁剪，无权视图不返回） */
    @RequiresPerm("wf:task:list")
    @GetMapping("/views")
    public Result<List<String>> views() {
        boolean superView = UserContext.getRequired().hasPerm("wf:instance:list");
        List<String> visible = TaskPoolQueryService.VIEWS.stream()
                .filter(v -> superView || !"POOL_ALL".equals(v))
                .toList();
        return Result.success(visible);
    }

    /** 我的待办 / 可认领 / 被他人锁定 / 我已认领（当前登录用户视角） */
    @RequiresPerm("wf:task:list")
    @GetMapping("/mine")
    public Result<PageResult<Map<String, Object>>> mine(@RequestParam String view,
                                                       PageQuery q,
                                                       @RequestParam(required = false) String keyword) {
        Long userId = UserContext.getRequired().getUserId();
        return Result.success(poolQueryService.view(view, q, userId, keyword));
    }

    /** 池内全部（共享中心主管总览，不限候选人；行内仍带锁定状态与候选人名单） */
    @RequiresPerm("wf:instance:list")
    @GetMapping("/all")
    public Result<PageResult<Map<String, Object>>> all(PageQuery q,
                                                      @RequestParam(required = false) String keyword) {
        return Result.success(poolQueryService.view("POOL_ALL", q, null, keyword));
    }
}
